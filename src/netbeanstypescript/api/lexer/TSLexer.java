/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2015 Everlaw. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package netbeanstypescript.api.lexer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

/**
 * This lexer is incomplete, but is only used for syntax highlighting, braces matching, and such -
 * not for parsing.
 * @author jeffrey
 */
public class TSLexer implements Lexer<JsTokenId> {

    static final HashMap<CharSequence, JsTokenId> keywords = new HashMap<>();
    static {
        for (JsTokenId token: JsTokenId.values()) {
            if (token.primaryCategory().equals("keyword")) {
                keywords.put(token.fixedText(), token);
            }
        }
    }

    // Keywords which can be immediately followed by an expression 
    static final EnumSet<JsTokenId> operatorKeywords = EnumSet.of(
            JsTokenId.TYPESCRIPT_AWAIT,
            JsTokenId.KEYWORD_CASE,
            JsTokenId.KEYWORD_DELETE,
            JsTokenId.KEYWORD_IN,
            JsTokenId.KEYWORD_INSTANCEOF,
            JsTokenId.KEYWORD_NEW,
            JsTokenId.KEYWORD_RETURN,
            JsTokenId.KEYWORD_THROW,
            JsTokenId.KEYWORD_TYPEOF,
            JsTokenId.KEYWORD_VOID,
            JsTokenId.RESERVED_YIELD);

    LexerInput input;
    TokenFactory<JsTokenId> factory;

    private static final int OPERATOR = 0; // term expected (/ is regexp)
    private static final int TERM = 1; // operator expected (/ is division)
    private static final int DOT = 2; // identifier expected
    int lastTokType;
    // While inside a `${ ... }` expression, this keeps track of the nesting of `${...}` (true)
    // and all other {...} (false).
    ArrayList<Boolean> braceStack = new ArrayList<>();

    private class State {
        int lastTokType;
        ArrayList<Boolean> braceStack;
    }

    public TSLexer(LexerRestartInfo<JsTokenId> info) {
        input = info.input();
        factory = info.tokenFactory();
        State s = (State) info.state();
        if (s != null) {
            lastTokType = s.lastTokType;
            braceStack.addAll(s.braceStack);
        }
    }

    boolean isWhiteSpace(int ch) {
        return ch == ' ' || ch == '\t' || ch == 0x000B || ch == '\f' ||
               ch == 0x00A0 || ch == 0x1680 || (ch >= 0x2000 && ch <= 0x200B) ||
               ch == 0x202F || ch == 0x205F || ch == 0x3000 || ch == 0xFEFF;
    }

    boolean isLineBreak(int ch) {
        return ch == '\n' || ch == '\r' || ch == 0x2028 || ch == 0x2029 || ch == 0x0085;
    }

    boolean isDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }

    private Token<JsTokenId> decimal() {
        int ch;
        do {
            ch = input.read();
        } while (isDigit(ch));
        if (ch == 'E' || ch == 'e') {
            ch = input.read();
            if (ch == '+' || ch == '-') {
                ch = input.read();
            }
            while (isDigit(ch)) {
                ch = input.read();
            }
        }
        input.backup(1);
        lastTokType = TERM;
        return factory.createToken(JsTokenId.NUMBER);
    }

    private Token<JsTokenId> scanString(int quote) {
        boolean escape = false;
        while (true) {
            int ch = input.read();
            if (ch == LexerInput.EOF) {
                input.backup(1);
                return factory.createToken(JsTokenId.ERROR);
            }
            if (escape) {
                escape = false;
            } else {
                if (ch == quote) {
                    lastTokType = TERM;
                    return factory.createToken(JsTokenId.STRING);
                }
                if (quote == '`') {
                    if (ch == '$') {
                        if (input.read() == '{') {
                            braceStack.add(Boolean.TRUE);
                            lastTokType = OPERATOR;
                            return factory.createToken(JsTokenId.STRING);
                        }
                        input.backup(1);
                    }
                } else {
                    if (isLineBreak(ch)) {
                        input.backup(1);
                        return factory.createToken(JsTokenId.ERROR);
                    }
                }
                escape = ch == '\\';
            }
        }
    }

    @Override
    public Token<JsTokenId> nextToken() {
        int ch = input.read();
        switch (ch) {
            case '\n':
            case '\r':
                return factory.createToken(JsTokenId.EOL);
            case '\t':
            case '\u000B':
            case '\f':
            case ' ':
                return factory.createToken(JsTokenId.WHITESPACE);
            case '!':
                if (input.read() == '=') {
                    lastTokType = OPERATOR;
                    if (input.read() == '=') {
                        return factory.createToken(JsTokenId.OPERATOR_NOT_EQUALS_EXACTLY);
                    }
                    input.backup(1);
                    return factory.createToken(JsTokenId.OPERATOR_NOT_EQUALS);
                }
                // lastTokType left as is (may be postfix non-null assertion)
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_NOT);
            case '"':
            case '\'':
            case '`':
                return scanString(ch);
            case '%':
                lastTokType = OPERATOR;
                if (input.read() == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_MODULUS_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_MODULUS);
            case '&':
                lastTokType = OPERATOR;
                ch = input.read();
                if (ch == '&') {
                    return factory.createToken(JsTokenId.OPERATOR_AND);
                }
                if (ch == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_BITWISE_AND_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_BITWISE_AND);
            case '(':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.BRACKET_LEFT_PAREN);
            case ')':
                lastTokType = TERM;
                return factory.createToken(JsTokenId.BRACKET_RIGHT_PAREN);
            case '*':
                lastTokType = OPERATOR;
                if (input.read() == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_MULTIPLICATION_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_MULTIPLICATION);
            case '+':
                ch = input.read();
                if (ch == '+') {
                    // lastTokType left as is
                    return factory.createToken(JsTokenId.OPERATOR_INCREMENT);
                }
                lastTokType = OPERATOR;
                if (ch == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_PLUS_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_PLUS);
            case ',':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_COMMA);
            case '-':
                ch = input.read();
                if (ch == '-') {
                    // lastTokType left as is
                    return factory.createToken(JsTokenId.OPERATOR_DECREMENT);
                }
                lastTokType = OPERATOR;
                if (ch == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_MINUS_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_MINUS);
            case '.':
                ch = input.read();
                if (isDigit(ch)) {
                    return decimal();
                }
                if (ch == '.') {
                    if (input.read() == '.') {
                        return factory.createToken(JsTokenId.OPERATOR_DOT_DOT_DOT);
                    }
                    input.backup(1);
                }
                input.backup(1);
                lastTokType = DOT;
                return factory.createToken(JsTokenId.OPERATOR_DOT);
            case '/':
                ch = input.read();
                if (ch == '/') {
                    do {
                        ch = input.read();
                    } while (ch != LexerInput.EOF && ! isLineBreak(ch));
                    input.backup(1);
                    return factory.createToken(JsTokenId.LINE_COMMENT);
                }
                if (ch == '*') {
                    int prev;
                    ch = 0;
                    do {
                        prev = ch;
                        ch = input.read();
                        if (ch == LexerInput.EOF) {
                            input.backup(1);
                            return factory.createToken(JsTokenId.ERROR);
                        }
                    } while (prev != '*' || ch != '/');
                    return factory.createToken(JsTokenId.BLOCK_COMMENT);
                }
                if (lastTokType == OPERATOR) {
                    input.backup(1);
                    return reScanSlashToken();
                }
                lastTokType = OPERATOR;
                if (ch == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_DIVISION_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_DIVISION);
            case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                do {
                    ch = input.read();
                } while (Character.isLetterOrDigit(ch));
                // TODO: hex/octal literals can't have decimals
                if (ch == '.') {
                    return decimal();
                }
                input.backup(1);
                lastTokType = TERM;
                return factory.createToken(JsTokenId.NUMBER);
            case ':':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_COLON);
            case ';':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_SEMICOLON);
            case '<':
                // TODO
                lastTokType = OPERATOR;
                ch = input.read();
                if (ch == '/') {
                    return factory.createToken(JsTokenId.OPERATOR_LESS_THAN_SLASH);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_LOWER);
            case '=':
                // TODO
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_ASSIGNMENT);
            case '>':
                // TODO
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_GREATER);
            case '?':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_TERNARY);
            case '@':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_AT);
            case '[':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.BRACKET_LEFT_BRACKET);
            case ']':
                lastTokType = TERM;
                return factory.createToken(JsTokenId.BRACKET_RIGHT_BRACKET);
            case '^':
                lastTokType = OPERATOR;
                if (input.read() == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_BITWISE_XOR_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_BITWISE_XOR);
            case '{':
                if (braceStack.size() > 0) {
                    braceStack.add(Boolean.FALSE);
                }
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.BRACKET_LEFT_CURLY);
            case '|':
                lastTokType = OPERATOR;
                ch = input.read();
                if (ch == '|') {
                    return factory.createToken(JsTokenId.OPERATOR_OR);
                }
                if (ch == '=') {
                    return factory.createToken(JsTokenId.OPERATOR_BITWISE_OR_ASSIGNMENT);
                }
                input.backup(1);
                return factory.createToken(JsTokenId.OPERATOR_BITWISE_OR);
            case '}':
                if (braceStack.size() > 0 && braceStack.remove(braceStack.size() - 1)) {
                    return scanString('`');
                }
                lastTokType = TERM;
                return factory.createToken(JsTokenId.BRACKET_RIGHT_CURLY);
            case '~':
                lastTokType = OPERATOR;
                return factory.createToken(JsTokenId.OPERATOR_BITWISE_NOT);
            case '\\':
                // TODO
                return factory.createToken(JsTokenId.ERROR);
            default:
                if (Character.isJavaIdentifierStart(ch)) {
                    while (Character.isJavaIdentifierPart(input.read())) {}
                    input.backup(1);
                    if (lastTokType != DOT) {
                        JsTokenId tok = keywords.get(input.readText());
                        if (tok != null) {
                            lastTokType = operatorKeywords.contains(tok) ? OPERATOR : TERM;
                            return factory.createToken(tok);
                        }
                    }
                    lastTokType = TERM;
                    return factory.createToken(JsTokenId.IDENTIFIER);
                } else if (isWhiteSpace(ch)) {
                    return factory.createToken(JsTokenId.WHITESPACE);
                } else if (isLineBreak(ch)) {
                    return factory.createToken(JsTokenId.EOL);
                }
                return factory.createToken(JsTokenId.ERROR);
            case LexerInput.EOF:
                return null;
        }
    }

    private Token<JsTokenId> reScanSlashToken() {
        boolean inEscape = false;
        boolean inCharacterClass = false;
        while (true) {
            // If we reach the end of a file, or hit a newline, then this is an unterminated
            // regex.  Report error and return what we have so far.
            int ch = input.read();
            if (ch == LexerInput.EOF || isLineBreak(ch)) {
                input.backup(1);
                return factory.createToken(JsTokenId.ERROR);
            }

            if (inEscape) {
                // Parsing an escape character;
                // reset the flag and just advance to the next char.
                inEscape = false;
            } else if (ch == '/' && ! inCharacterClass) {
                // A slash within a character class is permissible,
                // but in general it signals the end of the regexp literal.
                break;
            } else if (ch == '[') {
                inCharacterClass = true;
            } else if (ch == '\\') {
                inEscape = true;
            } else if (ch == ']') {
                inCharacterClass = false;
            }
        }

        while (Character.isJavaIdentifierPart(input.read())) {}
        input.backup(1);
        return factory.createToken(JsTokenId.REGEXP);
    }

    @Override
    public Object state() {
        State s = null;
        if (lastTokType != 0 || ! braceStack.isEmpty()) {
            s = new State();
            s.lastTokType = lastTokType;
            s.braceStack = new ArrayList<>(braceStack);
        }
        return s;
    }

    @Override
    public void release() {}
}
