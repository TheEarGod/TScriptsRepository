package net.runelite.client.plugins.tscripts.lexer;

import lombok.Data;
import net.runelite.client.plugins.tscripts.api.MethodManager;
import net.runelite.client.plugins.tscripts.lexer.Scope.Scope;
import net.runelite.client.plugins.tscripts.lexer.Scope.condition.*;
import net.runelite.client.plugins.tscripts.lexer.models.Element;
import net.runelite.client.plugins.tscripts.lexer.models.Token;
import net.runelite.client.plugins.tscripts.lexer.models.TokenType;
import net.runelite.client.plugins.tscripts.lexer.variable.AssignmentType;
import net.runelite.client.plugins.tscripts.lexer.variable.VariableAssignment;
import org.apache.commons.lang3.NotImplementedException;
import java.rmi.UnexpectedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Lexer class is responsible for parsing the tokens into an AST.
 */
@Data
public class Lexer
{
    private boolean verify = true;
    private boolean debug = false;
    private List<String> userFunctions = new ArrayList<>();

    /**
     * Lexes the tokens into a scope.
     * @param tokens the tokens to lex
     * @return the scope
     * @throws Exception if an error occurs
     */
    public static Scope lex(List<Token> tokens) throws Exception {
        return new Lexer().parse(tokens);
    }

    /**
     * Parses the tokens into a scope.
     * @param tokens the tokens to parse
     * @return the scope
     * @throws Exception if an error occurs
     */
    public Scope parse(List<Token> tokens) throws Exception
    {
        return flushScope(tokens, null);
    }

    /**
     * processes the tokens into a scope.
     * @param tokens the tokens to flush
     * @param conditions the conditions
     * @return the scope
     * @throws Exception if an error occurs
     */
    private Scope flushScope(List<Token> tokens, Conditions conditions) throws Exception
    {
        Map<Integer, Element> elements = new HashMap<>();
        int elementsPointer = 0;

        ElemType currentType = null;
        Conditions _conditions = null;
        String userFunctionName = null;
        List<Token> segment = new ArrayList<>();
        int depthCounter = -1;

        int pointer = -1;
        for(Token token : tokens)
        {
            pointer++;

            if (token.getType().equals(TokenType.COMMENT))
            {
                continue;
            }

            if (currentType != null)
            {
                switch (currentType)
                {
                    case VARIABLE_ASSIGNMENT:
                        if (depthCounter == -1)
                        {
                            depthCounter = 1;
                        } else
                        {
                            depthCounter++;
                        }
                        segment.add(token);

                        if (tokens.get(pointer + 1).getType().equals(TokenType.SEMICOLON))
                        {
                            elements.put(elementsPointer++, flushVariableAssignment(new ArrayList<>(segment)));
                            segment.clear();
                            depthCounter = -1;
                            currentType = null;
                        }
                        continue;
                    case SCOPE:
                        if (depthCounter == -1 && tokens.get(pointer - 1).getType().equals(TokenType.OPEN_BRACE))
                        {
                            depthCounter = 1;
                            segment.add(token);
                            continue;
                        }

                        switch (token.getType())
                        {
                            case CLOSE_BRACE:
                                depthCounter--;
                                if (depthCounter != 0)
                                {
                                    segment.add(token);
                                }
                                break;
                            case OPEN_BRACE:
                                depthCounter++;
                                segment.add(token);
                                break;
                            default:
                                segment.add(token);
                        }

                        if (depthCounter == 0)
                        {
                            currentType = null;
                            depthCounter = -1;
                            elements.put(elementsPointer++, flushScope(new ArrayList<>(segment), (_conditions != null ? _conditions.clone() : null)));
                            segment.clear();
                            _conditions = null;
                        }

                        continue;
                    case FUNCTION:
                        if (depthCounter == -1 && token.getType().equals(TokenType.OPEN_PAREN))
                        {
                            depthCounter = 1;
                            segment.add(token);
                            continue;
                        }

                        switch (token.getType())
                        {
                            case OPEN_PAREN:
                                depthCounter++;
                                segment.add(token);
                                break;
                            case CLOSE_PAREN:
                                depthCounter--;
                                segment.add(token);
                                break;
                            default:
                                segment.add(token);
                        }

                        if (depthCounter == 0)
                        {
                            currentType = null;
                            depthCounter = -1;
                            elements.put(elementsPointer++, flushFunction(new ArrayList<>(segment)));
                            segment.clear();
                        }
                        continue;
                    case USER_DEFINED_FUNCTION:
                    case CONDITION:
                        if (depthCounter == -1 && token.getType().equals(TokenType.OPEN_PAREN))
                        {
                            depthCounter = 1;
                            segment.add(token);
                            continue;
                        }
                        else if (depthCounter == -1 && token.getType().equals(TokenType.IDENTIFIER) && userFunctionName == null)
                        {
                            userFunctionName = token.getValue();
                            continue;
                        }

                        switch (token.getType())
                        {
                            case OPEN_PAREN:
                                depthCounter++;
                                segment.add(token);
                                break;
                            case CLOSE_PAREN:
                                depthCounter--;
                                segment.add(token);
                                break;
                            default:
                                segment.add(token);
                        }

                        if (depthCounter == 0)
                        {
                            currentType = null;
                            depthCounter = -1;
                            _conditions = flushConditions(segment);
                            if(_conditions.getType().equals(ConditionType.USER_DEFINED_FUNCTION) && userFunctionName != null)
                            {
                                userFunctions.add(userFunctionName.toLowerCase());
                                _conditions.setUserFunctionName(userFunctionName);
                                userFunctionName = null;
                            }
                            if (!tokens.get(pointer + 1).getType().equals(TokenType.OPEN_BRACE))
                            {
                                throw new UnexpectedException("Lexer::parseScope[CONDITION->SCOPE] unexpected value, expected start of scope [T:" + pointer + "] got [" + tokens.get(pointer + 1).getType().name() + "]");
                            }
                            segment.clear();
                        }
                        continue;
                }
            }


            if (token.getType().equals(TokenType.KEYWORD_IF))
            {
                segment.add(token);
                currentType = ElemType.CONDITION;
            }
            else if (token.getType().equals(TokenType.KEYWORD_WHILE))
            {
                segment.add(token);
                currentType = ElemType.CONDITION;
            }
            else if (token.getType().equals(TokenType.KEYWORD_SUBSCRIBE))
            {
                segment.add(token);
                currentType = ElemType.CONDITION;
            }
            else if (token.getType().equals(TokenType.KEYWORD_USER_DEFINED_FUNCTION))
            {
                segment.add(token);
                currentType = ElemType.USER_DEFINED_FUNCTION;
            }
            else if (token.getType().equals(TokenType.OPEN_BRACE))
            {
                currentType = ElemType.SCOPE;
            }
            else if (token.getType().equals(TokenType.IDENTIFIER))
            {
                segment.add(token);
                currentType = ElemType.FUNCTION;
            }
            else if (token.getType().equals(TokenType.VARIABLE))
            {
                TokenType btt = tokens.get(pointer + 1).getType();
                if (!btt.equals(TokenType.VARIABLE_ASSIGNMENT) && !btt.equals(TokenType.VARIABLE_INCREMENT) && !btt.equals(TokenType.VARIABLE_DECREMENT) && !btt.equals(TokenType.VARIABLE_ADD_ONE) && !btt.equals(TokenType.VARIABLE_REMOVE_ONE))
                {
                    throw new UnexpectedException("Lexer::parseScope[VARIABLE] unexpected value, expected VALUE_ASSIGNMENT token [T:" + (pointer + 1) + "] got [" + tokens.get(pointer + 1).getType().name() + "] on line {" + token.getLine() + "}");
                }
                segment.add(token);
                currentType = ElemType.VARIABLE_ASSIGNMENT;
            }
            else if (token.getType().equals(TokenType.NEGATE))
            {
                segment.add(token);
            }
        }

        return new Scope(elements, conditions);
    }

    /**
     * processes the tokens into a variable assignment.
     * @param tokens the tokens to flush
     * @return the variable assignment
     * @throws Exception if an error occurs
     */
    private VariableAssignment flushVariableAssignment(List<Token> tokens) throws Exception
    {
        String _variable = null;
        List<Object> _values = new ArrayList<>();
        AssignmentType _type = null;
        boolean inMethodCall = false;
        int depthCounter = -1;
        List<Token> segment = new ArrayList<>();

        for(Token token : tokens)
        {
            if(token.getType().equals(TokenType.IDENTIFIER))
            {
                inMethodCall = true;
            }
            else if(token.getType().equals(TokenType.NEGATE) && !inMethodCall)
            {
                segment.add(token);
                continue;
            }

            if(inMethodCall)
            {
                if(depthCounter == -1 && token.getType().equals(TokenType.OPEN_PAREN))
                {
                    depthCounter = 1;
                    segment.add(token);
                    continue;
                }

                switch (token.getType())
                {
                    case OPEN_PAREN:
                        depthCounter++;
                        segment.add(token);
                        break;
                    case CLOSE_PAREN:
                        depthCounter--;
                        segment.add(token);
                        break;
                    default:
                        segment.add(token);
                }

                if(depthCounter == 0)
                {
                    inMethodCall = false;
                    depthCounter = -1;
                    _values.add(flushFunction(new ArrayList<>(segment)));
                    segment.clear();
                }
                continue;
            }

            boolean negated = false;

            if(!segment.isEmpty() && segment.get(0).getType().equals(TokenType.NEGATE))
            {
                negated = true;
                segment.clear();
            }

            switch (token.getType())
            {
                case VARIABLE:
                    if(_variable == null)
                        _variable = token.getValue();
                    else
                        _values.add(token.getValue());
                    break;
                case STATIC_VALUE:
                    throw new NotImplementedException("Lexer::flushFunction static values are not implemented");
                case BOOLEAN:
                    if(negated)
                        _values.add(!token.getValue().equalsIgnoreCase("true"));
                    else
                        _values.add(token.getValue().equalsIgnoreCase("true"));
                    break;
                case INTEGER:
                    _values.add(Integer.parseInt(token.getValue()));
                    break;
                case STRING:
                    _values.add(token.getValue());
                    break;
                case VARIABLE_ASSIGNMENT:
                    _type = AssignmentType.ASSIGNMENT;
                    break;
                case VARIABLE_INCREMENT:
                    _type = AssignmentType.INCREMENT;
                    break;
                case VARIABLE_DECREMENT:
                    _type = AssignmentType.DECREMENT;
                    break;
                case VARIABLE_ADD_ONE:
                    _type = AssignmentType.ADD_ONE;
                    break;
                case VARIABLE_REMOVE_ONE:
                    _type = AssignmentType.REMOVE_ONE;
                    break;
            }
        }

        return new VariableAssignment(_variable, _values, _type);
    }

    /**
     * processes the tokens into a function.
     * @param tokens the tokens to flush
     * @return the method call
     * @throws Exception if an error occurs
     */
    private MethodCall flushFunction(List<Token> tokens) throws Exception
    {
        boolean negated = false;
        int pointer = 0;
        if(tokens.get(pointer).getType().equals(TokenType.NEGATE))
        {
            negated = true;
            pointer++;
        }
        String name = tokens.get(pointer).getValue();
        List<Object> _values = new ArrayList<>();
        List<Token> segment = new ArrayList<>();
        boolean inMethodCall = false;
        int depthCounter = -1;

        pointer += 2;

        for(int i = pointer; i <= tokens.size() - 1; i++)
        {
            Token token = tokens.get(i);
            if(token.getType().equals(TokenType.IDENTIFIER) && tokens.get(i + 1).getType().equals(TokenType.OPEN_PAREN))
            {
                inMethodCall = true;
            }

            if(inMethodCall)
            {
                if(depthCounter == -1 && token.getType().equals(TokenType.OPEN_PAREN))
                {
                    depthCounter = 1;
                    segment.add(token);
                    continue;
                }

                switch (token.getType())
                {
                    case OPEN_PAREN:
                        depthCounter++;
                        segment.add(token);
                        break;
                    case CLOSE_PAREN:
                        depthCounter--;
                        segment.add(token);
                        break;
                    default:
                        segment.add(token);
                        break;
                }

                if(depthCounter == 0)
                {
                    inMethodCall = false;
                    depthCounter = -1;
                    _values.add(flushFunction(new ArrayList<>(segment)));
                    segment.clear();
                }
                continue;
            }

            switch (token.getType())
            {
                case VARIABLE:
                case IDENTIFIER:
                case STRING:
                    _values.add(token.getValue());
                    break;
                case STATIC_VALUE:
                    throw new NotImplementedException("Lexer::flushFunction static values are not implemented");
                case BOOLEAN:
                    _values.add(token.getValue().equalsIgnoreCase("true"));
                    break;
                case INTEGER:
                    _values.add(Integer.parseInt(token.getValue()));
                    break;

            }
        }
        MethodCall methodCall = new MethodCall(name, _values.toArray(), negated);
        MethodManager.CHECK_RESPONSE check = MethodManager.getInstance().check(methodCall);
        if(!check.equals(MethodManager.CHECK_RESPONSE.OK) && !userFunctions.contains(name.toLowerCase()))
        {
            throw new UnexpectedException("Lexer::flushFunction method '" + name + "' contained errors: " + check.name() + " on line {" + tokens.get(0).getLine() + "}");
        }
        return methodCall;
    }

    private Conditions flushConditions(List<Token> tokens) throws Exception
    {
        Conditions conditions = new Conditions();
        ConditionType type;
        switch (tokens.get(0).getType())
        {
            case KEYWORD_IF:
                type = ConditionType.IF;
                break;
            case KEYWORD_WHILE:
                type = ConditionType.WHILE;
                break;
            case KEYWORD_SUBSCRIBE:
                type = ConditionType.SUBSCRIBE;
                break;
            case KEYWORD_USER_DEFINED_FUNCTION:
                type = ConditionType.USER_DEFINED_FUNCTION;
                break;
            default:
                throw new UnexpectedException("Lexer::flushCondition unexpected condition type  on line {" + tokens.get(0).getLine() + "}");
        }

        conditions.setType(type);

        List<Token> tokenList = new ArrayList<>();
        for(int i = 2; i < tokens.size() - 1; i++)
        {
            Token token = tokens.get(i);

            if(token.getType().equals(TokenType.CONDITION_AND) || token.getType().equals(TokenType.CONDITION_OR))
            {
                Glue glue = token.getType().equals(TokenType.CONDITION_AND) ? Glue.AND : Glue.OR;
                conditions.getConditions().put(conditions.getConditions().size(), flushCondition(tokenList));
                conditions.getGlues().put(conditions.getConditions().size() - 1, glue);
                tokenList.clear();
            }
            else
            {
                tokenList.add(token);
            }
        }
        conditions.getConditions().put(conditions.getConditions().size(), flushCondition(tokenList));
        return conditions;
    }

    /**
     * processes the tokens into a condition.
     * @param tokens the tokens to flush
     * @return the condition
     * @throws Exception if an error occurs
     */
    private Condition flushCondition(List<Token> tokens) throws Exception
    {
        Object left = null;
        Object right = null;
        Comparator comparator = null;
        Token tok;
        List<Token> segment = new ArrayList<>();
        boolean inMethodCall = false;
        int depthCounter = -1;
        for(int i = 0; i < tokens.size(); i++)
        {
            tok = tokens.get(i);

            if(tok.getType().equals(TokenType.IDENTIFIER) && tokens.size() > i + 1 && tokens.get(i + 1).getType().equals(TokenType.OPEN_PAREN))
            {
                inMethodCall = true;
            }
            else if (tok.getType().equals(TokenType.NEGATE))
            {
                segment.add(tok);
            }

            if(inMethodCall)
            {
                if (depthCounter == -1)
                {
                    segment.add(tok);
                    tok = tokens.get(++i);
                    segment.add(tok);
                    depthCounter = 1;
                }
                while(inMethodCall)
                {
                    tok = tokens.get(++i);
                    switch (tok.getType())
                    {
                        case OPEN_PAREN:
                            depthCounter++;
                            segment.add(tok);
                            break;
                        case CLOSE_PAREN:
                            depthCounter--;
                            segment.add(tok);
                            break;
                        default:
                            segment.add(tok);
                    }
                    if (depthCounter == 0)
                    {
                        inMethodCall = false;
                    }
                }

                depthCounter = -1;
                if(left == null)
                {
                    left = flushFunction(new ArrayList<>(segment));
                }
                else if(right == null)
                {
                    right = flushFunction(new ArrayList<>(segment));
                }
                segment.clear();
                continue;
            }

            boolean negated = false;
            switch (tok.getType())
            {
                case CONDITION_GT:
                case CONDITION_LT:
                case CONDITION_GTEQ:
                case CONDITION_LTEQ:
                case CONDITION_EQ:
                case CONDITION_NEQ:
                    comparator = Comparator.fromBaseTokenType(tok.getType());
                    break;
                case INTEGER:
                    if(left == null)
                    {
                        left = Integer.parseInt(tok.getValue());
                    }
                    else if(right == null)
                    {
                        right = Integer.parseInt(tok.getValue());
                    }
                    else throw new UnexpectedException("Lexer::flushCondition[" + tok.getType() + "] unexpected value in condition on line {" + tok.getLine() + "}");
                    break;
                case BOOLEAN:
                    if(!segment.isEmpty() && segment.get(0).getType().equals(TokenType.NEGATE))
                    {
                        negated = true;
                        segment.clear();
                    }
                    if(left == null)
                    {
                        left = tok.getValue().equalsIgnoreCase("true");
                        if(negated)
                            left = !(boolean)left;
                    }
                    else if(right == null)
                    {
                        right = tok.getValue().equalsIgnoreCase("true");
                        if(negated)
                            right = !(boolean)right;
                    }
                    else throw new UnexpectedException("Lexer::flushCondition[" + tok.getType() + "] unexpected value in condition on line {" + tok.getLine() + "}");
                    break;
                case IDENTIFIER:
                    if(tokens.get(i + 1).getType().equals(TokenType.OPEN_PAREN))
                    {
                        inMethodCall = true;
                        depthCounter = 1;
                        segment.add(tok);
                    }
                    else if(left == null)
                    {
                        left = tok.getValue();
                    }
                    else if(right == null)
                    {
                        right = tok.getValue();
                    }
                    else throw new UnexpectedException("Lexer::flushCondition[" + tok.getType() + "] unexpected value in condition on line {" + tok.getLine() + "}");
                    break;
                case STRING:
                    if(!segment.isEmpty() && segment.get(0).getType().equals(TokenType.NEGATE))
                    {
                        segment.clear();
                    }
                    if(left == null)
                    {
                        left = tok.getValue();
                    }
                    else if(right == null)
                    {
                        right = tok.getValue();
                    }
                    else throw new UnexpectedException("Lexer::flushCondition[" + tok.getType() + "] unexpected value in condition on line {" + tok.getLine() + "}");
                    break;
                case VARIABLE:
                    if(!segment.isEmpty() && segment.get(0).getType().equals(TokenType.NEGATE))
                    {
                        negated = true;
                        segment.clear();
                    }
                    if(left == null)
                    {
                        left = tok.getValue();
                        if(negated)
                            left = "!" + left;
                    }
                    else if(right == null)
                    {
                        right = tok.getValue();
                        if(negated)
                            right = "!" + right;
                    }
                    else throw new UnexpectedException("Lexer::flushCondition[" + tok.getType() + "] unexpected value in condition on line {" + tok.getLine() + "}");
                    break;
            }
        }

        return new Condition(left, right, comparator);
    }

    /**
     * The element type.
     */
    private enum ElemType
    {
        CONDITION,
        FUNCTION,
        SCOPE,
        USER_DEFINED_FUNCTION,
        VARIABLE_ASSIGNMENT
    }
}