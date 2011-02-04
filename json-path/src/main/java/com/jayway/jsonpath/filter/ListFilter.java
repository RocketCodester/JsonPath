package com.jayway.jsonpath.filter;

import com.jayway.jsonpath.JsonUtil;
import com.jayway.jsonpath.eval.Expression;
import org.json.simple.JSONArray;

//import javax.script.ScriptEngine;
//import javax.script.ScriptEngineManager;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: kallestenflo
 * Date: 2/2/11
 * Time: 2:32 PM
 */
public class ListFilter extends JsonPathFilterBase {

    //private static ScriptEngine SCRIPT_ENGINE = new ScriptEngineManager().getEngineByName("js");

    private static final Pattern LIST_INDEX_PATTERN = Pattern.compile("\\[(\\s?\\d+\\s?,?)+\\]"); //[1] OR [1,2,3]
    private static final Pattern LIST_PULL_PATTERN = Pattern.compile("\\[\\s?:(\\d+)\\s?\\]");   //[ :2 ]
    private static final Pattern LIST_WILDCARD_PATTERN = Pattern.compile("\\[\\*\\]");
    private static final Pattern LIST_TAIL_PATTERN_SHORT = Pattern.compile("\\[\\s*-\\s*(\\d+):\\s*\\]");   // [(@.length - 12)] OR [-13:]
    private static final Pattern LIST_TAIL_PATTERN_LONG = Pattern.compile("\\[\\s*\\(\\s*@\\.length\\s*-\\s*(\\d+)\\s*\\)\\s*\\]");
    private static final Pattern LIST_TAIL_PATTERN = Pattern.compile("(" + LIST_TAIL_PATTERN_SHORT.pattern() + "|" + LIST_TAIL_PATTERN_LONG.pattern() + ")");
    private static final Pattern LIST_ITEM_HAS_PROPERTY_PATTERN = Pattern.compile("\\[\\s?\\?\\s?\\(\\s?@\\.(\\w+)\\s?\\)\\s?\\]");
    private static final Pattern LIST_ITEM_MATCHES_EVAL = Pattern.compile("\\[\\s?\\?\\s?\\(\\s?@.(\\w+)\\s?([=<>]+)\\s?(.*)\\s?\\)\\s?\\]");    //[ ?( @.title< 'ko' ) ]

    private final String pathFragment;

    public ListFilter(String pathFragment) {
        this.pathFragment = pathFragment;
    }

    @Override
    public List<Object> apply(List<Object> items) {
        List<Object> result = new JSONArray();

        if (LIST_INDEX_PATTERN.matcher(pathFragment).matches()) {
            return filterByListIndex(items);
        } else if (LIST_WILDCARD_PATTERN.matcher(pathFragment).matches()) {
            return filterByWildcard(items);
        } else if (LIST_TAIL_PATTERN.matcher(pathFragment).matches()) {
            return filterByListTailIndex(items);
        } else if (LIST_PULL_PATTERN.matcher(pathFragment).matches()) {
            return filterByPullIndex(items);
        } else if (LIST_ITEM_HAS_PROPERTY_PATTERN.matcher(pathFragment).matches()) {
            return filterByItemProperty(items);
        } else if (LIST_ITEM_MATCHES_EVAL.matcher(pathFragment).matches()) {
            return filterByItemEvalMatch(items);
        }

        return result;
    }

    private List<Object> filterByItemEvalMatch(List<Object> items) {
        List<Object> result = new JSONArray();

        for (Object current : items) {
            for (Object item : JsonUtil.toList(current)) {
                if (isEvalMatch(item)) {
                    result.add(item);
                }
            }
        }
        return result;
    }


    private List<Object> filterByItemProperty(List<Object> items) {
        List<Object> result = new JSONArray();

        String prop = getFilterProperty();

        for (Object current : items) {
            for (Object item : JsonUtil.toList(current)) {

                if (JsonUtil.isMap(item)) {
                    if (JsonUtil.toMap(item).containsKey(prop)) {
                        result.add(item);
                    }
                }
            }
        }
        return result;
    }


    private List<Object> filterByWildcard(List<Object> items) {
        List<Object> result = new JSONArray();

        for (Object current : items) {
            result.addAll(JsonUtil.toList(current));
        }
        return result;
    }

    private List<Object> filterByListTailIndex(List<Object> items) {
        List<Object> result = new JSONArray();


        for (Object current : items) {
            List array = JsonUtil.toList(current);
            result.add(array.get(getTailIndex(array.size())));
        }
        return result;
    }

    private List<Object> filterByListIndex(List<Object> items) {
        List<Object> result = new JSONArray();

        for (Object current : items) {
            Integer[] index = getArrayIndex();
            for (int i : index) {

                result.add(JsonUtil.toList(current).get(i));
            }
        }
        return result;
    }

    private List<Object> filterByPullIndex(List<Object> items) {
        List<Object> result = new JSONArray();

        for (Object current : items) {
            Integer[] index = getListPullIndex();
            for (int i : index) {

                result.add(JsonUtil.toList(current).get(i));
            }
        }
        return result;
    }

    private boolean isEvalMatch(Object check) {
        Matcher matcher = LIST_ITEM_MATCHES_EVAL.matcher(pathFragment);

        if (matcher.matches()) {
            String property = matcher.group(1);
            String operator = matcher.group(2);
            String expected = matcher.group(3);

            if (!JsonUtil.isMap(check)) {
                return false;
            }
            Map obj = JsonUtil.toMap(check);

            if (!obj.containsKey(property)) {
                return false;
            }

            Object propertyValue = obj.get(property);

            if (JsonUtil.isContainer(propertyValue)) {
                return false;
            }

            String expression = propertyValue + " " + operator + " " + expected;
            System.out.println("EVAL" + expression);


            return Expression.eval(propertyValue, operator, expected);

        }
        return false;
    }

    private String getFilterProperty() {
        Matcher matcher = LIST_ITEM_HAS_PROPERTY_PATTERN.matcher(pathFragment);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("invalid list filter property");
    }

    private int getTailIndex(int arraySize) {

        Matcher matcher = LIST_TAIL_PATTERN_SHORT.matcher(pathFragment);
        if (matcher.matches()) {

            int index = Integer.parseInt(matcher.group(1));

            return arraySize - index;
        }
        matcher = LIST_TAIL_PATTERN_LONG.matcher(pathFragment);
        if (matcher.matches()) {

            int index = Integer.parseInt(matcher.group(1));

            return arraySize - index;
        }

        throw new IllegalArgumentException("invalid list index");

    }

    private Integer[] getListPullIndex() {
        Matcher matcher = LIST_PULL_PATTERN.matcher(pathFragment);
        if (matcher.matches()) {

            int pullCount = Integer.parseInt(matcher.group(1));

            List<Integer> result = new LinkedList<Integer>();

            for (int y = 0; y < pullCount; y++) {
                result.add(y);
            }
            return result.toArray(new Integer[0]);
        }
        throw new IllegalArgumentException("invalid list index");
    }

    private Integer[] getArrayIndex() {

        String prepared = pathFragment.replaceAll(" ", "");
        prepared = prepared.substring(1, prepared.length() - 1);

        List<Integer> index = new LinkedList<Integer>();

        String[] split = prepared.split(",");

        for (String s : split) {
            index.add(Integer.parseInt(s));
        }
        return index.toArray(new Integer[0]);
    }

}