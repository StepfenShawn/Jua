package com.stepfenshawn;

import java.io.*;
import java.util.*;
import java.nio.charset.Charset;;

public class Jua{
    public static void main(String[] args) {
        new Jua().interpret(readFile(args[0]));
    }

    private enum TokenType {
        WORD, NUMBER, STRING, LINE,
        EQUALS, OPERATOR, LEFT_PAREN, RIGHT_PAREN, EOF
    }

    private enum TokenizeState {
        DEFAULT, WORD, NUMBER, STRING, COMMENT
    }
    
    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<Token>();
        String token = "";
        TokenizeState state = TokenizeState.DEFAULT;
        /* TODO: use a map */
        String charTokens = "\n=+-*/<>()";
        TokenType[] tokenTypes = { TokenType.LINE, TokenType.EQUALS,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.OPERATOR, TokenType.OPERATOR, TokenType.OPERATOR,
            TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN
        };
        
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (state) {
            case DEFAULT:
                if (charTokens.indexOf(c) != -1) {
                    tokens.add(new Token(Character.toString(c), tokenTypes[charTokens.indexOf(c)]));
                } else if (Character.isLetter(c)) {
                    token += c;
                    state = TokenizeState.WORD;
                } else if (Character.isDigit(c)) {
                    token += c;
                    state = TokenizeState.NUMBER;
                } else if (c == '"') {
                    state = TokenizeState.STRING;
                } else if (c == '\'') {
                    state = TokenizeState.COMMENT;
                }
                break;
                
            case WORD:
                if (Character.isLetterOrDigit(c)) {
                    token += c;
                }
                else {
                    tokens.add(new Token(token, TokenType.WORD));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--;
                }
                break;
                
            case NUMBER:
                if (Character.isDigit(c)) {
                    token += c;
                } else {
                    tokens.add(new Token(token, TokenType.NUMBER));
                    token = "";
                    state = TokenizeState.DEFAULT;
                    i--;
                }
                break;
                
            case STRING:
                if (c == '"') {
                    tokens.add(new Token(token, TokenType.STRING));
                    token = "";
                    state = TokenizeState.DEFAULT;
                } else {
                    token += c;
                }
                break;
                
            case COMMENT:
                if (c == '\n') {
                    state = TokenizeState.DEFAULT;
                }
                break;
            }
        }
        
        return tokens;
    }
    
    private static class Token {
        public final String text;
        public final TokenType type;

        public Token(String text, TokenType type) {
            this.text = text;
            this.type = type;
        }
    }

    private class Parser {
        public Parser(List<Token> tokens) {
            this.tokens = tokens;
            position = 0;
        }
        
        public List<Statement> parse(Map<String, Integer> labels) {
            List<Statement> statements = new ArrayList<Statement>();
            
            while (true) {
                while (match(TokenType.LINE));

                if (match(TokenType.WORD, TokenType.EQUALS)) {
                    String name = last(2).text;
                    Expression value = expression();
                    statements.add(new AssignStatement(name, value));
                } else if (match("print")) {
                    statements.add(new PrintStatement(expression()));
                }
                else if (match("if")) {
                    Expression condition = expression();
                    consume("then");
                    String label = consume(TokenType.WORD).text;
                    statements.add(new IfThenStatement(condition, label));
                } else break;
            }
            
            return statements;
        }
        
        private Expression expression() {
            return operator();
        }
        
        private Expression operator() {
            Expression expression = atomic();
            while (match(TokenType.OPERATOR) ||
                   match(TokenType.EQUALS)) {
                char operator = last(1).text.charAt(0);
                Expression right = atomic();
                expression = new OperatorExpression(expression, operator, right);
            }
            
            return expression;
        }
        
        private Expression atomic() {
            if (match(TokenType.WORD)) {
                return new VariableExpression(last(1).text);
            } else if (match(TokenType.NUMBER)) {
                return new NumberValue(Double.parseDouble(last(1).text));
            } else if (match(TokenType.STRING)) {
                return new StringValue(last(1).text);
            } else if (match(TokenType.LEFT_PAREN)) {
                Expression expression = expression();
                consume(TokenType.RIGHT_PAREN);
                return expression;
            }
            throw new Error("Couldn't parse :(");
        }
        
        private boolean match(TokenType type1, TokenType type2) {
            if (get(0).type != type1) return false;
            if (get(1).type != type2) return false;
            position += 2;
            return true;
        }
        
        private boolean match(TokenType type) {
            if (get(0).type != type) return false;
            position++;
            return true;
        }
        
        private boolean match(String name) {
            if (get(0).type != TokenType.WORD) return false;
            if (!get(0).text.equals(name)) return false;
            position++;
            return true;
        }
        
        private Token consume(TokenType type) {
            if (get(0).type != type) throw new Error("Expected " + type + ".");
            return tokens.get(position++);
        }
        
        private Token consume(String name) {
            if (!match(name)) throw new Error("Expected " + name + ".");
            return last(1);
        }

        private Token last(int offset) {
            return tokens.get(position - offset);
        }
        
        private Token get(int offset) {
            if (position + offset >= tokens.size()) {
                return new Token("", TokenType.EOF);
            }
            return tokens.get(position + offset);
        }
        
        private final List<Token> tokens;
        private int position;
    }
    
    public interface Statement {
        void execute();
    }

    public interface Expression {
        Value evaluate();
    }
    
    public class PrintStatement implements Statement {
        public PrintStatement(Expression expression) {
            this.expression = expression;
        }
        
        public void execute() {
            System.out.println(expression.evaluate().toString());
        }

        private final Expression expression;
    }
    
    public class AssignStatement implements Statement {
        private final String name;
        private final Expression value;

        public AssignStatement(String name, Expression value) {
            this.name = name;
            this.value = value;
        }
        
        public void execute() {
            variables.put(name, value.evaluate());
        }
    }
    
    public class IfThenStatement implements Statement {
        private final Expression condition;
        private final String label;

        public IfThenStatement(Expression condition, String label) {
            this.condition = condition;
            this.label = label;
        }
        
        public void execute() {
            if (labels.containsKey(label)) {
                double value = condition.evaluate().toNumber();
                if (value != 0) {
                    currentStatement = labels.get(label).intValue();
                }
            }
        }
    }
    
  
    public class VariableExpression implements Expression {
        private final String name;
        public VariableExpression(String name) {
            this.name = name;
        }
        
        public Value evaluate() {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            return new NumberValue(0);
        }
    }
    
    public class OperatorExpression implements Expression {
        private final Expression left;
        private final char operator;
        private final Expression right;
        public OperatorExpression(Expression left, char operator, Expression right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
        
        public Value evaluate() {
            Value leftVal = left.evaluate();
            Value rightVal = right.evaluate();
            
            switch (operator) {
            case '=':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() ==
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue(leftVal.toString().equals(
                                           rightVal.toString()) ? 1 : 0);
                }
            case '+':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue(leftVal.toNumber() +
                                           rightVal.toNumber());
                } else {
                    return new StringValue(leftVal.toString() +
                            rightVal.toString());
                }
            case '-':
                return new NumberValue(leftVal.toNumber() -
                        rightVal.toNumber());
            case '*':
                return new NumberValue(leftVal.toNumber() *
                        rightVal.toNumber());
            case '/':
                return new NumberValue(leftVal.toNumber() /
                        rightVal.toNumber());
            case '<':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() <
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue((leftVal.toString().compareTo(
                                           rightVal.toString()) < 0) ? 1 : 0);
                }
            case '>':
                if (leftVal instanceof NumberValue) {
                    return new NumberValue((leftVal.toNumber() >
                                            rightVal.toNumber()) ? 1 : 0);
                } else {
                    return new NumberValue((leftVal.toString().compareTo(
                            rightVal.toString()) > 0) ? 1 : 0);
                }
            }
            throw new Error("Unknown operator.");
        }
    }
    public interface Value extends Expression {
        String toString();
        double toNumber();
    }
    
    public class NumberValue implements Value {
        private final double value;
        public NumberValue(double value) {
            this.value = value;
        }
        
        @Override public String toString() { return Double.toString(value); }
        public double toNumber() { return value; }
        public Value evaluate() { return this; }
    }
    
    public class StringValue implements Value {
        private final String value;
        public StringValue(String value) {
            this.value = value;
        }
        
        @Override public String toString() { return value; }
        public double toNumber() { return Double.parseDouble(value); }
        public Value evaluate() { return this; }
    }

    public Jua() {
        variables = new HashMap<String, Value>();
        labels = new HashMap<String, Integer>();
        
        InputStreamReader converter = new InputStreamReader(System.in);
        lineIn = new BufferedReader(converter);
    }
    
    private final Map<String, Value> variables;
    private final Map<String, Integer> labels;
    private final BufferedReader lineIn;
    private int currentStatement;
    
    public void interpret(String source) {
        List<Token> tokens = tokenize(source);    
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parse(labels);
        currentStatement = 0;

        while (currentStatement < statements.size()) {
            int thisStatement = currentStatement;
            currentStatement++;
            statements.get(thisStatement).execute();
        }
    }
    
    private static String readFile(String path) {
        try {
            InputStreamReader input = new InputStreamReader(new FileInputStream(path), Charset.defaultCharset());
            Reader reader = new BufferedReader(input);
            
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                builder.append(buffer, 0, read);
            }
            builder.append('\n');
            return builder.toString();
        } catch (IOException ex) {
            return null;
        }
    }
}