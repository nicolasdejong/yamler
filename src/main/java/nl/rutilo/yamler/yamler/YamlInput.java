package nl.rutilo.yamler.yamler;

import nl.rutilo.yamler.yamler.exceptions.YamlerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Character.isWhitespace;

public class YamlInput {
    final YamlerConfig config;
    final char[] input;
    int offset;
    int indent;
    int posInLine;
    int lineNo;

    private static final int MAX_POS_PRE = 60;

    public static class State {
        private final int offset;
        private final int indent;
        private final int posInLine;
        private final int lineNo;
        private State(YamlInput toCopy) {
            this.offset = toCopy.offset;
            this.indent = toCopy.indent;
            this.posInLine = toCopy.posInLine;
            this.lineNo = toCopy.lineNo;
        }
        private void copyInto(YamlInput target) {
            target.offset = this.offset;
            target.indent = this.indent;
            target.posInLine = this.posInLine;
            target.lineNo = this.lineNo;
        }
    }

    public YamlInput(char[] input) {
        this(YamlerConfig.DEFAULT, input);
    }
    public YamlInput(YamlerConfig config, char[] input) {
        this.config = config;
        this.input = input;
        this.offset = 0;
        this.indent = 0;
        this.posInLine = 0;
        this.lineNo = 0;
    }

    //<editor-fold desc="Getting and setting state">
    public State getState() { return new State(this); }
    public void setState(State state) { state.copyInto(this); stateWasReset(); }

    private final List<Runnable> onStateResetRunners = new ArrayList<>();
    protected void onStateReset(Runnable r) { onStateResetRunners.add(r); }
    protected void stateWasReset() {
        onStateResetRunners.forEach(Runnable::run);
    }
    //</editor-fold>

    //<editor-fold desc="Error handling">
    YamlerException error(String... error)  {
        return new YamlerException(String.join(" ", error) + " on line " + lineNumber() + ":\n" + posText());
    }
    int lineStartPos()        { int pos = Math.min(offset, input.length-1); while(pos>=0 && input[pos]!='\n') pos--; return pos+1; } // NOSONAR
    int lineNumber()          { int n=1; int pos = Math.min(offset, input.length-1); while(pos>0) if(input[pos--]=='\n') n++; return n; } // NOSONAR
    String posText()          {
        if(offset < input.length && input[offset] == '\n') offset++;
        int linePos  = lineStartPos();
        int arrowPos = offset - linePos;
        int lineLen  = Math.min(input.length, offset + 10) - linePos;
        String prefix = "";
        if(arrowPos > MAX_POS_PRE) {
            final int delta = arrowPos - MAX_POS_PRE;
            linePos += delta;
            lineLen -= delta;
            arrowPos -= delta;
            prefix = "...";
            arrowPos += 3;
        }
        final String line  = prefix + new String(input, linePos, lineLen).replaceAll("(?s)\n.*$", "");
        final String arrow = " ".repeat(Math.max(0,arrowPos)) + "^";
        return line + "\n" + arrow;
    }
    //</editor-fold>

    //<editor-fold desc="Debug logging">
    private String getOffsetString(int... maxWidthOpt) {
        final String s = String.valueOf(input, offset, input.length - offset).replace("\n","\\n");
        final int maxWidth = maxWidthOpt.length > 0 ? maxWidthOpt[0] : s.length();
        String result = maxWidth < s.length() ? s.substring(0, maxWidth) : s;
        return result + " ".repeat(Math.max(0, maxWidth - result.length()));
    }
    void log(Object... args) {
        System.out.println("i" + indent + "]>" + getOffsetString(22) + " -- " // NOSONAR -- *this* is the logger
            + Arrays.stream(args)
            .map(a -> a==null?"null":a.toString())
            .collect(Collectors.joining(" "))
            .replace("\n","\\n"));
    }
    //</editor-fold>

    boolean ended()           { return offset >= input.length; }
    boolean ended(int delta)  { return offset + delta >= input.length; }
    int c()                   { return ended() ? -1 : input[offset]; }
    int c(int delta)          { return offset + delta < 0 || offset + delta >= input.length ? -1 : input[offset + delta]; }
    int next()                {
        if(!ended()) {
            if(c()=='\n') {
                lineNo++; offset++; indent = 0; posInLine=0;
                while(c() == ' ') { indent++; offset++; posInLine++; }
            } else {
                offset++; posInLine++;
            }
        }
        return c();
    }

    int next(int delta)       { for(int i=0; i<delta; i++) { next(); } return c(); }

    boolean isOneOf(String s) { return isOneOf(s, c()); }
    boolean isOneOf(String s, int c) { return s.length() > 0 && s.indexOf(c) >= 0; }

    void skipSpaces()         { while(isSpace((char)c())) next(); }
    void skipWhitespaces()    { while(isWhitespace(c())) next(); }
    boolean skipIf(int c)     { return c() == c && next() > 0; }
    boolean isSpace(int c)    { return c == ' ' || c == '\t'; }
    boolean is(String s)      {
        for(int i=0; i<s.length(); i++) if(c(i) != s.charAt(i)) return false; // about 10% faster than toCharArray() first
        return true;
    }

    String readUntil(char... endChars) {
        int startOffset = offset;
        int n = 0;
        if(endChars == null || endChars.length == 0) return "";

        while(!ended()) {
            n = c(n) == endChars[n] ? n+1 : 0;
            if(n == endChars.length) break;
            if(n == 0) next(); // next() has side effects (on newline) so can't go back, hence the c(n))
        }
        return n == endChars.length ? new String(input, startOffset, offset - startOffset) : "";
    }

    String readStringUntilWhitespace() { return readStringUntilWhitespaceOr(null); }
    String readStringUntilWhitespaceOr(String chars) {
        final StringBuilder sb = new StringBuilder();
        skipWhitespaces();
        while(!isWhitespace(c()) && (chars == null || chars.isEmpty() || chars.indexOf(c())<0) && !ended()) {
            sb.append((char)c()); next();
        }
        return sb.toString();
    }
}

