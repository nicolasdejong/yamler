package nl.rutilo.yamler.yamler;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import nl.rutilo.yamler.utils.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Character.isWhitespace;

@SuppressWarnings({"squid:S1452","squid:S2160", "squid:S3358", "squid:S3776", "squid:S5411"})
// S1452: Internally using wildcards for a scalar, which can be String and Number
// S2160: No need to add compare() when no state is added in subclass -- Sonar does not recognize 'transient'.
// S3358: Don't nest conditionals -- Can be more readable if written concise
// S3776: Yes, some code has too high cyclox
// S5411: Primitive objects shouldn't be null anyway
public class YamlTokenizer extends YamlScalarReader {
    private boolean skipRemarks = true;

    @RequiredArgsConstructor
    public enum TokenType {
        EMPTY,
        REMARK,
        LIST_START('['),
        LIST_END(']'),
        LIST_ITEM('-'),
        MAP_START('{'),
        MAP_END('}'),
        MAP_KEY_FOLLOWS('?'),
        SCALAR,
        REF('&'),
        USE_REF('*'),
        SEPARATOR(','),
        SEPARATOR_KV(':'),
        TAG,
        END_DOC,
        END
        ;
        final char character;
        TokenType() { character = 0; }
    }
    @Builder
    public static class Token {
        final TokenType type;
        final Object value;    // |
        final int offset;
        final int lineNo;
        final int indent;
        final int posInLine;   // \
        public boolean isType(TokenType... types) {
            for (TokenType tokenType : types) if (type == tokenType) return true;
            return false;
        }
        public String toString() {
            return "[TOKEN; type=" + type + (indent > 0 ? "; ind=" + indent : "") + "; line=" + lineNo + " pil=" + posInLine + (value == null ? "" : "; value=" + value) + "]";
        }
        public String toShortString() {
            return "[" + type
                + (value == null ? "" : "|" + value)
                + (posInLine > 0 ? "\\" + posInLine : "")
                + "]";
        }

        private static final Pattern FROM_STRING_PATTER = Pattern.compile("^(.*?)(?:\\|([^\\^]+))?(?:\\^(.+))?$");
        public static Token fromString(String s) {
            final Matcher matcher = FROM_STRING_PATTER.matcher(s);
            if(!matcher.matches()) throw new IllegalArgumentException("Cannot create token from: " + s);
            final String setType = matcher.group(1);
            final String setValue= matcher.group(2);
            final String setPil  = matcher.group(3);

            return new TokenBuilder()
                .type(Enum.valueOf(TokenType.class, setType))
                .value(setValue == null ? null : Value.or(YamlNumbers.toNumber(setValue).map(Object::toString),
                                                          Value.of(setValue).filter(p->p.length()>0)
                                                         ).orElse(null))
                .posInLine(setPil == null ? 0 : YamlNumbers.toNumber(setPil).orElse(0).intValue())
                .build();
        }
    }

    public YamlTokenizer(String yamlText) {
        this(YamlerConfig.DEFAULT, yamlText);
    }
    public YamlTokenizer(YamlerConfig config, String yamlText) {
        super(config, yamlText.toCharArray());
        onStateReset(() -> nextToken = null);
    }

    public YamlTokenizer setSkipRemarks(boolean set) { skipRemarks = set; return this; }
    public boolean isSkipRemarks() { return skipRemarks; }

    // Currently only used by tests
    public List<Token> tokenize() {
        final List<Token> tokens = new ArrayList<>();

        while(!ended()) {
            tokens.add(nextToken());
        }

        return tokens;
    }

    private Token.TokenBuilder tokenBuilder(TokenType type) { return tokenBuilder(type, 0); }
    private Token.TokenBuilder tokenBuilder(TokenType type, int xrel) {
        return Token.builder()
            .type(type)
            .offset(offset + xrel)
            .lineNo(lineNo)
            .posInLine(posInLine + xrel)
            .indent(indent);
    }

    private Token nextToken = null;

    // Note that with peeking:
    // - indent & posInLine will be updated
    public Token peekToken() {
        if(nextToken != null) return nextToken;
        final Token token = nextToken(/*peeking=*/true);
        nextToken = token;
        return token;
    }

    public boolean skipIfToken(TokenType type) {
        return (peekToken().type == type && nextToken() != null);
    }

    private boolean inMapKey = false; // how about recursion? (key consists of map that has keys)
    private Runnable onNextToken = null;

    public Token nextToken() { return nextToken(/*peeking=*/false); }
    private Token nextToken(boolean peeking) {
        if(onNextToken != null) { onNextToken.run(); onNextToken = null; }
        if(nextToken != null) {
            final Token token = nextToken;
            nextToken = null;
            return token;
        }
        skipWhitespaces();
        final Token token;
        final boolean inFlowMap = flowMapDepth > 0;
        final boolean inFlowList = flowListDepth > 0;
        final int beforeIndent = indent;
        final int beforePosInLine = posInLine;
        Runnable changeState = null;

        switch(c()) {
            case '?': token = tokenBuilder(TokenType.MAP_KEY_FOLLOWS).build(); next(); changeState = () -> inMapKey = true; break;
            case '{': token = tokenBuilder(TokenType.MAP_START ).build(); next(); changeState = () -> flowMapDepth++;  inMapKey = true; break;
            case '}': token = tokenBuilder(TokenType.MAP_END   ).build(); next(); changeState = () -> flowMapDepth--;  inMapKey = false; break;
            case '[': token = tokenBuilder(TokenType.LIST_START).build(); next(); changeState = () -> flowListDepth++; break;
            case ']': token = tokenBuilder(TokenType.LIST_END  ).build(); next(); changeState = () -> flowListDepth--; break;
            case -1:  token = tokenBuilder(TokenType.END).posInLine(-1).build(); next(); break;
            case '|':
            case '>':
            case '`':
            case '"':
            case '\'': {
                final Token.TokenBuilder builder = tokenBuilder(TokenType.SCALAR);
                readString().ifPresentOrElse(builder::value, () -> builder.type(TokenType.EMPTY));
                token = builder.build();
                break;
            }
            case '*':
                // refs have zero width (including the following spaces)
                next();
                final String refName = readStringUntilWhitespaceOr(inFlowList ? "]," : inFlowMap ? "}," : "");
                skipSpaces();
                indent = beforeIndent;
                posInLine = beforePosInLine;
                token = tokenBuilder(TokenType.USE_REF).value(refName).build();
                break;
            case '&':
                // refs have zero width (including the following spaces)
                next();
                final String useRefName = readStringUntilWhitespaceOr(inFlowList ? "]," : inFlowMap ? "}," : "");
                skipSpaces();
                indent = beforeIndent;
                posInLine = beforePosInLine;
                token = tokenBuilder(TokenType.REF).value(useRefName).build();
                break;
            case '%':
                next();
                readUntil('\n'); // directive is not supported -- skipped
                return nextToken();
            default:
                Token wsToken = null;
                if (c() == '/' && c(1) == '*') {
                    next(2); // skip /*
                    wsToken = tokenBuilder(TokenType.REMARK, -2).value(readUntil('*', '/')).build();
                    next(2); // skip */
                } else
                if(c() == ',' && (inFlowMap || inFlowList)) {
                    if(inFlowMap && config.disallowColonsInUnquotedKeys) inMapKey = true;
                    wsToken = tokenBuilder(TokenType.SEPARATOR).build();
                    next();
                } else
                if(c() == ':' && (inFlowMap || (inMapKey && config.disallowColonsInUnquotedKeys))) {
                    wsToken = tokenBuilder(TokenType.SEPARATOR_KV).build();
                    next();
                } else
                if(c() == '!' && c(1) == '!') {
                    // tags have zero width (including the following spaces)
                    next(2);
                    wsToken = tokenBuilder(TokenType.TAG)
                        .posInLine(posInLine-2)
                        .value(readStringUntilWhitespaceOr(",[]{}")).build();
                    skipSpaces();
                    indent = beforeIndent;
                    posInLine = beforePosInLine;
                } else
                if(c() == '!') { // no support for things like !local
                    next();
                    // ignored but error if tag is not empty or local
                    if(!isWhitespace(c())) {
                        final String tag = readStringUntilWhitespace();
                        if(!tag.equals("local")) throw error("Non-local tags not supported by this implementation: " + tag);
                    }
                    skipSpaces();
                    indent = beforeIndent;
                    posInLine = beforePosInLine;
                    return nextToken();
                } else
                if(isWhitespace(c(1))) { // some tokens need a following whitespace
                    switch(c()) {
                        case ':': wsToken = tokenBuilder(TokenType.SEPARATOR_KV).build(); next(); break;
                        case '-': wsToken = tokenBuilder(TokenType.LIST_ITEM).build(); next(); break;
                        case '#': next(2); wsToken = tokenBuilder(TokenType.REMARK).posInLine(posInLine-2).value(readUntil('\n')).build(); break;
                        default: wsToken = null;
                    }
                } else
                if((is("---") || is("...")) && (isWhitespace(c(3)) || ended(3))) {
                    wsToken = tokenBuilder(TokenType.END_DOC).build();
                    next(3);
                }

                // no else
                if(wsToken != null) {
                    token = wsToken;
                } else {
                    final Token.TokenBuilder builder = tokenBuilder(TokenType.SCALAR);
                    (inMapKey ? readScalarMapKey() : readScalar())
                        .ifPresentOrElse(builder::value, () -> builder.type(TokenType.EMPTY));
                    inMapKey = false;
                    token = builder.build();
                }
        }
        if(changeState != null) {
            if(peeking) onNextToken = changeState;
            else changeState.run();
        }

        return (skipRemarks && token.type == TokenType.REMARK) ? nextToken() : token;
    }
}
