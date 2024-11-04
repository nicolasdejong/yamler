package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.collections.StringKeyMap;
import nl.rutilo.yamler.utils.StringUtils;
import nl.rutilo.yamler.utils.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.lang.Character.isWhitespace;

@SuppressWarnings({
  "squid:S1452", // Internally using wildcards for a scalar, which can be String and Number
  "squid:S2160", // No need to add compare() when no state is added in subclass -- Sonar does not recognize 'transient'.
  "squid:S3358", // Don't nest conditionals -- Can be more readable if written concise
  "squid:S3776", // Yes, some code has too high cyclox -- logic is complicated and splitting it up will complicate it more
  "squid:S5411", // Primitive objects shouldn't be null anyway
  "squid:S135"   // Logic is too complicated to move all conditions in the loop condition
})
public class YamlScalarReader extends YamlInput {
    private int quote; // ` (back tick) treated like " here -- ' has no escapes, except '' which is '
    private TextBlockType blockType;
    private ChompType chompType;
    private int startIndent;
    private boolean isOwnIndent;
    private final StringBuilder sb = new StringBuilder();
    private boolean stop;
    private int radix = 10;
    private State lastNewlineState = null;
    private boolean isMapKey = false;
    private final StringBuilder indentText = new StringBuilder();

    int flowMapDepth = 0;       // In any child of flow map   -- Set in Tokenizer
    int flowListDepth = 0;      // In any child of flow list  -- Set in Tokenizer
    boolean inFlowMap = false;  // Current depth is flow map  -- Set in Tokenizer
    boolean inFlowList = false; // Current depth is flow list -- Set in Tokenizer

    Pattern varPattern = null;
    private static final Map<String,Pattern> varPatternCache = new HashMap<>();

    public YamlScalarReader(char[] input) {
        this(YamlerConfig.DEFAULT, input);
    }
    public YamlScalarReader(YamlerConfig config, char[] input) {
        super(config, input);
        //noinspection ConstantConditions <-- false positive
        if(config.variableSyntax != null) { // this value *can* be non-null
            varPattern = varPatternCache.computeIfAbsent(config.variableSyntax, varSyntax -> {
                final String[] parts = varSyntax.split("var", 2);
                return Pattern.compile("\\\\?" + Pattern.quote(parts[0]) + "([\\w._]+)" + (parts.length>1 ? Pattern.quote(parts[1]) : ""));
            });
        }
    }

    enum ChompType {
        CLIP,  //   put a single newline at end of block (default)
        STRIP, // - strip breaks at end of block
        KEEP   // + keep breaks at end of block
    }
    enum TextBlockType {
        BASIC,   // (default)
        LITERAL, // | keep line breaks, except multiple breaks at end of block
        FOLDED   // > keep line breaks, except multiple breaks at end of block
    }

    private static final StringKeyMap SCALAR_CONSTANTS = new StringKeyMap(
        "true",  true,
        "True",  true,
        "TRUE",  true,

        "false", false,
        "False", false,
        "FALSE", false,

        "null",  null,
        "Null",  null,
        "NULL",  null,
        "~",     null,

        "undefined", null,

        ".inf", Double.POSITIVE_INFINITY,
        ".Inf", Double.POSITIVE_INFINITY,
        ".INF", Double.POSITIVE_INFINITY,

        "-.inf", Double.NEGATIVE_INFINITY,
        "-.Inf", Double.NEGATIVE_INFINITY,
        "-.INF", Double.NEGATIVE_INFINITY,

        ".nan", Double.NaN,
        ".NaN", Double.NaN,
        ".NAN", Double.NaN
    ).setUseDottedPaths(false);

    Value<String> readString() {
        return readScalar().notNull().map(Object::toString);
    }

    /** Scalar type can be any one of: null, boolean, Number, String */
    Value<?> readScalar() {
        sb.setLength(0);
        stop = false;
        radix = 10;
        init();
        return quote != 0           ? getQuoted() .map(this::replaceVars) :
               blockType == TextBlockType.BASIC   ? getBasic()  .map(this::replaceVars) :
               blockType == TextBlockType.LITERAL ? getLiteral().map(this::replaceVars) :
               blockType == TextBlockType.FOLDED  ? getFolded() .map(this::replaceVars) : Value.empty();
    }

    private <T> T replaceVars(T in) {
        //noinspection unchecked
        return varPattern == null || (config.variables == null && config.variableGetter == null) || !(in instanceof String) ? in :
            (T)StringUtils.replaceMatches((String)in, varPattern, matcher -> {
                final String name = matcher.group(1);
                if(matcher.group().startsWith("\\")) return matcher.group(); // escape
                return config.variableGetter != null
                  ? config.variableGetter.apply(name)
                  : Objects.toString(Value.or(config.variables.get(name), "?"));
            });
    }

    Value<?> readScalarMapKey() {
        isMapKey = true;
        final Value<?> result = readScalar();
        isMapKey = false;
        return result;
    }

    private void init() {
        skipWhitespaces();
        quote = isOneOf("\"'`") ? c() : 0;
        startIndent = -1;
        if(quote != 0) {
            this.blockType = TextBlockType.BASIC;
            next(); // skip quote
            // rest is unused in quoted mode
            chompType = ChompType.KEEP;
            startIndent = 0;
            return;
        }
        final State stateBeforeRead = getState();
        lastNewlineState = null;
        blockType   = skipIf('|') ? TextBlockType.LITERAL :
                      skipIf('>') ? TextBlockType.FOLDED : TextBlockType.BASIC;

        // startIndent should ignore empty lines because editors often remove ending spaces of a line
        if(blockType != TextBlockType.BASIC  && c() >= '1' && c() <= '9') {
            startIndent = c() - '0';
            next(); // skip digit
        }
        chompType   = blockType == TextBlockType.BASIC ? ChompType.CLIP :
                      skipIf('+') ? ChompType.KEEP :
                      skipIf('-') ? ChompType.STRIP :
                      ChompType.CLIP;

        if(startIndent < 0) {
            if(blockType == TextBlockType.BASIC && c() != '\n') {
                startIndent = indent;
                isOwnIndent = startIndent == posInLine;
            } else {
                startIndent = findBlockIndent();
            }
        }

        skipSpaces();

        if(c() == '#' && isSpace(c(1))) readUntil('\n'); // skip remark

        if(blockType != TextBlockType.BASIC && c() != '\n') {
            blockType = TextBlockType.BASIC;
        } else
        if(blockType == TextBlockType.BASIC) {
            int i = -1;
            while(offset + i > 0 && isSpace(c(i))) i--;
        }

        if(blockType == TextBlockType.BASIC) {
            // check if the following is a number
            if(c() == '-' || c() == '+') { sb.append((char)c()); next(); }
            if(c() == '0'){
                if(c(1) == 'x') { radix = 16; next(2); sb.append("0x"); } else
                if(c(1) == 'o' || (c(1) >= '0' && c(1) <= '6')) { radix = 7; next(2); sb.append("0o"); }
            }
            boolean hasDot = false;
            boolean hasExp = false;
            while( (radix == 10 && ((c() >= '0' && c() <= '9') || (!hasDot && c() == '.') || (!hasExp && c() == 'E')))
                ||  (radix ==  7 &&   c() >= '0' && c() <= '6')
                ||  (radix == 16 && ((c() >= '0' && c() <= '9') || (c() >= 'A' && c() <= 'F') || (c() >= 'a' && c() <= 'f')))
            ) {
                final char c = (char)c();
                if(radix == 10 && c == '.') hasDot = true;
                if(radix == 10 && c == 'E') hasExp = true;
                sb.append(c);
                next();
            }
            skipSpaces();
            if(c() == '\n') {
                lastNewlineState = getState();
            } else {
                stop = ((flowMapDepth > 0 || flowListDepth > 0) && c() == ',') || ("]}".indexOf(c()) >= 0);
            }

            // reset state if not a number
            if(!stop) {
                sb.setLength(0);
                setState(stateBeforeRead);
            }
        } else {
            if(c() != '\n') throw error("Unexpected character in " + blockType + " scalar: " + (char)c());
            next();
            if(indent > startIndent) sb.append(" ".repeat(indent - startIndent));
            else {
                if(startIndent == 0) {
                    int nCount = 0;
                    while(c() == '\n') { nCount++; next(); }
                    if(nCount > 0) sb.append("\n".repeat(nCount));
                    stop = nCount > 0;
                }
            }
        }
    }

    private Value<String> getQuoted() {
        while( !stop && !ended()) {
            int c = c();

            // double single quote escapes quote
            if(quote == '\'' && c == quote && c(1) == quote) {
                next();
            } else

            // double quoted with escape
            if(quote == '"' && c == '\\') {
                next();
                c = c();
                if(c == '\n') { next(); continue; }

                final int cEscaped = escapeOf(c);
                if(cEscaped >= 0) c = cEscaped; else sb.append("\\");
            } else

            if(c == '\n') {
                trimTrailingSpaces();
                next();
                skipSpaces();
                c = c();
                if( c == '\n' ) skipWhitespaces();
                else if(quote == '"' && c == '\\' && c(1) == 'n') continue; else c = ' ' ;
                sb.append((char)c);
                continue;
            } else

            if(c == quote) {
                next();
                break;
            }

            //
            sb.append((char)c);
            nextDontSkipIndent();
        }
        return Value.of(sb.toString())
                    .filter(s -> !(s.isEmpty() && ended()));
    }
    private Value<?> getBasic() {
        int sbLenAtNewline = 0;

        while( !stop && !ended()) {
            int c = c();
            if(c == '\n') sbLenAtNewline = sb.length();

            // stop at line remark
            if(c() == '#' && (isSpace(c(-1)) || offset == 0) && (isSpace(c(1)) || ended(1))) break;

            // normal text is until terminator
            if(   ( flowListDepth > 0 && c == ']')
               || ( flowMapDepth  > 0 && c == '}')
               || ((flowMapDepth  > 0 || flowListDepth > 0) && c == ',')
               ) break;

            // key terminator (:) means the text should have stopped at end of previous line
            if(c == ':'
                        &&  (  isMapKey
                            || isWhitespace(c(1))
                            || ended(1)
                            || ",]}".indexOf(c(1)) >= 0
                            || SCALAR_CONSTANTS.containsKey(sb.toString())
            )) {
                if(lastNewlineState != null && !isMapKey && !inFlowMap) {
                    setState(lastNewlineState);
                    sb.setLength(sbLenAtNewline);
                    stateWasReset();
                }
                break;
            }

            // list item if first non-whitespace character
            if(c == '-' && isWhitespace(c(1)) && indent == posInLine) {
                break;
            } else

            if(c == '\n') {
                lastNewlineState = getState();
                trimTrailingWhitespaces();
                int skippedLines = 0;
                do { next(); skippedLines++; skipSpaces(); } while(c() == '\n'); // skip empty lines & indent

                if( isOwnIndent && posInLine < startIndent  && !inFlowMap) break;
                if(!isOwnIndent && posInLine <= startIndent && !inFlowMap) break;

                sb.append(skippedLines > 1 ? "\n".repeat(skippedLines-1) : " ");

                if(c(0)=='{' || c(0)=='[') break;
                if(c(0)=='-' && c(1)=='-' && c(2)=='-' && (isWhitespace(c(3)) || ended(3))) break;
                if(c(0)=='.' && c(1)=='.' && c(2)=='.' && (isWhitespace(c(3)) || ended(3))) break;
                if(c(0)=='!' && c(1)=='!' && c(2)!='!' && !isWhitespace(c(2)) && !ended(2)) break;
                if(c(0)=='#' && (isWhitespace(c(1)) || ended(1)) && isWhitespace(c(-1))) break;
                continue;
            }

            sb.append((char)c);
            nextDontSkipIndent();
        }

        final Value<?> knownType = stringToKnownType(sb.toString().trim());
        if (knownType.isPresent()) return knownType;
        trimTrailingWhitespaces();

        return Value.of(sb.toString())
                    .filter(s -> !(s.isEmpty() && ended()))
                    .map(s -> onlySpacesOrEmpty(s) ? null : s);
    }
    private Value<String> getLiteral() { // literal: |
        int blockIndent = -1;
        final int startLen = sb.length();

        while( !stop && !ended()) {
            int c = c();

            // Flow block only stops when indent is lower than startIndent (ignoring empty lines)
            if(indent < startIndent && !isWhitespace(c)) break;

            if(c == '\n') {
                next(); // skips indent
                if(indent == 0 && c() == '.' && c(1) == '.' && c(2) == '.' && c(3) == '\n') break;
                if(indent == 0 && c() == '-' && c(1) == '-' && c(2) == '-' && c(3) == '\n') break;

                final int extraIndent = c() == '\n' ? Math.max(0, indent - startIndent) : indent - startIndent;
                if(extraIndent < 0) stop = true;
                else { sb.append("\n"); sb.append(" ".repeat(extraIndent)); }
            } else {
                if(!isSpace(c) && blockIndent < 0) blockIndent = indent;

                sb.append((char)c);
                nextDontSkipIndent();
            }
        }
        if(sb.length() > startLen) sb.append("\n");

        chomp();
        return Value.of(sb.toString())
                    .filter(s -> !(s.isEmpty() && ended()));
    }
    private Value<String> getFolded() { // folded: >
        if(c() == '\n') sb.append("\n");

        // Flow block only stops when indent is lower than startIndent (ignoring empty lines)
        while( !stop && !ended() && (indent >= startIndent || c() == '\n')) {

            if(c() == '\n') {
                int oldIndent = indent;

                nextStoreIndent(); // skips and stores indentText

                while(c() == '\n') {
                    sb.append("\n");
                    nextStoreIndent();
                }
                if(indent < startIndent) break;

                if(indent == startIndent && sbLast() != '\n') sb.append(" ");
                if(indent == startIndent && oldIndent > startIndent) sb.append("\n");
                if(indent > startIndent) { sb.append("\n"); sb.append(indentText.substring(startIndent)); }
            } else {
                sb.append((char)c());
                nextDontSkipIndent();
            }
        }
        sb.append("\n");

        chomp();
        return Value.of(sb.toString())
                    .filter(s -> !(s.isEmpty() && ended()));
    }


    private void chomp() {
        if(quote != 0 || blockType == TextBlockType.BASIC) return;
        switch(chompType) {
            // The final line break and any trailing empty lines are considered to be part of the scalar’s content.
            // These additional lines are not subject to folding
            case KEEP: break;

            // default behaviour
            // The final line break character is preserved in the scalar’s content.
            // However, any trailing empty lines are excluded from the scalar’s content.
            case CLIP: trimTrailingEmptyLines(); break;

            // The final line break and any trailing empty lines are excluded from the scalar’s content.
            case STRIP: trimTrailingWhitespaces();
                break;
        }
    }

    int nextDontSkipIndent() {
        if(!ended()) {
            if(c()=='\n') {
                lineNo++; offset++; indent = 0; posInLine=0;
            } else {
                offset++; posInLine++;
            }
        }
        return c();
    }
    int nextStoreIndent() {
        if(!ended()) {
            if(c()=='\n') {
                lineNo++; offset++; indent = 0; posInLine=0; indentText.setLength(0);
                while(c() == ' ' || c() == '\t') { indentText.append((char)c()); indent++; offset++; posInLine++; }
            } else {
                offset++; posInLine++;
            }
        }
        return c();
    }

    private char sbLast() { return sb.length() == 0 ? 0 : sb.charAt(sb.length()-1); }

    private int findBlockIndent() {
        int extra = 0;

        // find first non-whitespace. Then find indent of that character
        while(isWhitespace(c(extra))) extra++;
        extra--;
        int nsPos = extra;
        while(isWhitespace(c(extra)) && c(extra) != '\n') extra--;
        int blockIndent = nsPos - extra;

        // If no block indent is found, take the min (>0) indent of empty lines
        if(blockIndent == 0) {
            extra = 0;
            int lastStart = 0;
            for(; extra < nsPos; extra++) {
                if(c(extra) == '\n') {
                    blockIndent = Math.max(blockIndent, extra-lastStart);
                    lastStart = extra + 1;
                }
            }
        }

        return blockIndent;
    }
    private int escapeOf(int character) { // this method may have side effects!!! (next(n))
        int c;
        switch(character) {
            case '\\': c = '\\'; break;
            case '"': c = '"'; break;
            case 'n': c = '\n'; break;
            case 'r': c = '\r'; break;
            case 't': c = '\t'; break;
            case 'b': c = '\b'; break;
            case 'f': c = '\f'; break;
            case '0': c = 0x0; break;
            case 'a': c = 0x7; break;
            case 'v': c = 0xB; break;
            case 'e': c = 0x1B; break;
            case '\u0020': c = 0x20; break;
            case 'N': c = 0x85; break;
            case '_': c = 0xA0; break;
            case 'L': c = '\u2028'; break;
            case 'P': c = '\u2029'; break;
            case 'x': c = hexToInt(c(1), c(2)); next(2); break; // NOSONAR -- magic
            case 'u': c = hexToInt(c(1), c(2), c(3), c(4)); next(4); break; // NOSONAR -- magic
            case 'U': c = hexToInt(c(1), c(2), c(3), c(4), c(5), c(6), c(7), c(8)); next(8); break; // NOSONAR -- magic -- may overflow: int vs char
            default: c = -1;
        }
        return c;
    }
    private void trimTrailingWhitespaces() {
        int i = sb.length() - 1;
        while(i >= 0 && isWhitespace(sb.charAt(i))) i--;
        sb.setLength(i + 1);
    }
    private void trimTrailingSpaces() {
        int i = sb.length() - 1;
        while(i >= 0 && isSpace(sb.charAt(i))) i--;
        sb.setLength(i + 1);
    }
    private void trimTrailingEmptyLines() {
        // find last non-whitespace, then find next newline and remove from there
        int i = Math.max(0, sb.length() - 1);
        while(i > 0 && isWhitespace(sb.charAt(i))) i--;
        while(i < sb.length() && sb.charAt(i) != '\n') i++;
        if(i < sb.length()) sb.setLength(i+1); // keep last newline, if any
    }
    private int hexToInt(int... chars) {
        final StringBuilder hexText = new StringBuilder();
        for(int c : chars) hexText.append((char)c);
        try {
            return Integer.parseInt(hexText.toString(), 16);
        } catch(final NumberFormatException ne) {
            throw error("Unable to convert hex to integer: " + hexText);
        }
    }
    private boolean onlySpacesOrEmpty(String s) {
        if(s.isEmpty()) return true;
        for(int i=s.length()-1; i>=0; i--) if(!Character.isSpaceChar(s.charAt(i))) return false;
        return true;
    }

    private static Value<?> stringToKnownType(String s) {
        final Value<Number> number = YamlNumbers.toNumber(s);
        if(number.isPresent()) return number;

        return SCALAR_CONSTANTS.getValue(s);
    }

}
