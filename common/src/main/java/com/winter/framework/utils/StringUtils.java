package com.winter.framework.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提供通用的字符串处理方法，继承自apache-commons的StringUtils类，
 * 同时也使用guava处理字符串的相关对象
 * @author xiadaru
 *
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {
    
    private static final String CHARSET_NAME= "UTF-8";

    public static boolean isNotEmpty(String string) {
        return !isEmpty(string);
    }

	public static boolean isEmpty(String string) {
		if (string == null)
			return true;
		return "".equals(string);
	}
    
	/**
	 * 如果字符串是null、空格或者"null"字符串，那么则返回true，否则返回false。
	 * @param str 入参，用于判断的字符串
	 * @return 如果字符串是null、空格或者"null"字符串，那么则返回true，反之返回false
	 */
	public static boolean isNull(String str) {
		return !notNull(str);
	}

	/**
	 * 判断一个字符串是否不为空。
	 *
	 * @param str 入参，用于判断的字符串
	 * @return 跟{@link StringUtils#isNull(String)}返回相反的值
	 */
	public static boolean notNull(String str) {
		return str != null && str.trim().length() > 0 && !str.equalsIgnoreCase("null");
	}

	/**
	 * 将字符串中所有空格都去掉。
	 *
	 * @param str 用于替换的字符串
	 * @return the 替换过后的字符串
	 */
	public static String trimAll(String str) {
		return str.replace(' ', '\0');
	}

	/**
	 * 判断str字符串是否包含在指定的字符串数组strs中
	 *
	 * @param str 用于判断的字符串
	 * @param strs 字符串数组
	 * @return 如果str在strs中，那么返回true，否则false
	 */
	public static boolean isIn(String str, String[] strs) throws Exception {
		if (str == null) {
			throw new Exception("Input String can not be empty!");
		}
		for (int i = 0, len = strs.length; i < len; i++) {
			if (str.equals(strs[i])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 判断suffix是否是给定string对象str的末尾，忽略大小写
	 *
	 * @param str 需要判断的字符串对象
	 * @param suffix 结尾字符串
	 * @return 忽略大小写的判断，如果suffix匹配str对象的末尾，则返回true，反之返回false
	 */
	public static boolean endWithIgnoreCase(String str, String suffix) throws Exception {
		if (str == null || suffix == null) {
			throw new Exception("Suffix string object can't be null.");
		}
		if (str.length() < suffix.length()) {
			return false;
		}
		return str.substring(str.length() - suffix.length()).equalsIgnoreCase(suffix);
	}

	/**
	 * 判断prefix是否是给定string对象str的开头，忽略大小写
	 *
	 * @param str 需要判断的字符串对象
	 * @param prefix 开头字符串
	 * @return 忽略大小写的判断，如果prefix匹配str对象的开头，则返回true，反之返回false
	 */
	public static boolean startWithIgnoreCase(String str, String prefix) {
		if (str == null || prefix == null || str.length() < prefix.length()) {
			throw new RuntimeException("Prefix string object can't be null.");
		}
		if (str.length() < prefix.length()) {
			return false;
		}
		return str.substring(0, prefix.length()).equalsIgnoreCase(prefix);
	}

	/**
	 * 根据value对象，返回一个boolean值.
	 *
	 * @param value 入参
	 * @param defaultValue 默认值
	 * @return 将value入参转换成boolean的值
	 */
	public static boolean getBoolean(Object value, boolean defaultValue) {
		if (value == null) {
			return defaultValue;
		} else {
			if (value instanceof Boolean) {
				return ((Boolean) value).booleanValue();
			} else if (value instanceof String) {
				String stringValue = (String) value;
				return StringUtils.isNull(stringValue) ? defaultValue : stringValue.equalsIgnoreCase(Boolean.TRUE.toString());
			} else if (value instanceof Number) {
				return ((Number) value).intValue() > 0;
			} else {
				return defaultValue;
			}
		}
	}

	/**
	 * 首字母大写.
	 *
	 * @param str 需要首字母大写的字符串
	 * @return 首字母大写过后的字符串
	 */
	public static String firstLetterToUpperCase(String str) {
		if (isNull(str)) {
			return null;
		} else if (str.length() == 1) {
			return str.toUpperCase();
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	/**
	 * 格式化字符串.
	 *
	 * @param format，类似"this is %s ,hello."
	 * @param args 需要替换模板里面对应字符的参数
	 * @return 替换过后的字符串
	 */
	public static String format(String format, Object... args) {
		return String.format(format, args);
	}

	/**
	* 将指定字符数组中的参数用指定的seperator连接起来，会自动剔除null字符
	* @param separator 连接符
	* @param strs 需要连接在基础字符串上的对象
	* @return 连接过后的字符串
	* @throws
	*/
	public static String join(String separator, Object... strs) {
		Joiner joiner = Joiner.on(separator).skipNulls();
		return joiner.join(strs);
	}

	/**
	 * 使用guava的分离器，对字符串进行拆分，并且忽略前导和尾部空白，
	 * 并且忽略分离过程中出现的空串。
	 *
	 * @param source 需要切分的字符串
	 * @param separator 分隔符
	 * @return 切分过后的字符串列表
	 */
	public static List<String> splitter(String source, String separator) {
		return Lists.newArrayList(Splitter.on(separator) //设置拆分字符串
				.trimResults() //移除结果字符串的前导空白和尾部空白
				.omitEmptyStrings() //从结果中自动忽略空字符串
				.split(source));
	}

	/**
	* 自定义的一个string模板处理方法，可以在string中加入${xxx}变量，然后程序里面生成对应的string 
	* @param strTemplate 字符串模板
	* @param conditions 条件map对象
	* @return 处理过后的string字符串
	* @throws
	 */
	public static String process(String strTemplate, Map<String, String> conditions) {
		if (!StringUtils.isNull(strTemplate)) {
			String regex = "\\$\\{\\w+\\}";
			Pattern p = Pattern.compile(regex);
			Matcher m = p.matcher(strTemplate);
			while (m.find()) {
				String mather = m.group();
				String paramName = mather.substring(2, mather.length() - 1);
				String replacement = conditions != null && !conditions.isEmpty() ? conditions.get(paramName) : null;
				if (!StringUtils.isBlank(replacement)) {
					strTemplate = StringUtils.replace(strTemplate, mather, replacement);
				} else {
					strTemplate = StringUtils.replace(strTemplate, mather, "");
				}
			}
			return strTemplate;
		}
		return "";
	}

	/**
	 * 转化字符串为指定字符集的字节数组;
	 * 为了兼容JDK1.5
	 * @param s
	 * @param charset
	 * @return
	 */
	public static byte[] stringToBytes(String s, Charset charset){
		try {
			return s.getBytes(charset.name());
		} catch (UnsupportedEncodingException e) {
			//该情况应该不会发生
			e.printStackTrace();
			throw new IllegalArgumentException(e);
		}
	}

	public static String randomUUID() {
		return UUID.randomUUID().toString()/*.replace("-", "")*/;
	}

	/**
	 * @Title: equals
	 * @Description: 判断两个字符串是否想等，都为空为想等
	 * @param: [str1, str2]
	 * @return: boolean
	 */
	public static boolean equals(String str1, String str2) {
		if (isNull(str1) && isNull(str2)) {
			return true;
		} else {
			if (notNull(str1)) {
				return str1.equals(str2);
			} else {
				return str2.equals(str1);
			}
		}
	}
	
	 /**
     * 转换为Double类型
     */
    public static Double toDouble(Object val){
        if(val==null){
            return 0D;
        }
        try{
            return Double.valueOf(trim(val.toString()));
        }
        catch(Exception e){
            return 0D;
        }
    }
    
    /**
     * 转换为Float类型
     */
    public static Float toFloat(Object val){
        return toDouble(val).floatValue();
    }
    
    /**
     * 转换为Long类型
     */
    public static Long toLong(Object val){
        return toDouble(val).longValue();
    }
    
    /**
     * 转换为Integer类型
     */
    public static Integer toInteger(Object val){
        return toLong(val).intValue();
    }
    
    /**
     * 转换为字节数组
     * @param str
     * @return
     */
    public static byte[] getBytes(String str){
        if(isNotEmpty(str)){
            try{
                return str.getBytes(CHARSET_NAME);
            }
            catch(UnsupportedEncodingException e){
                return null;
            }
        }
        else{
            return null;
        }
    }
    
    /**
     * 转换为字节数组
     * @param str
     * @return
     */
    public static String toString(byte[] bytes){
        try{
            return new String(bytes,CHARSET_NAME);
        }
        catch(UnsupportedEncodingException e){
            return EMPTY;
        }
    }
    
    /***
	 * 用于校验是否为数字
	 * @param num
	 * @return
	 */
	public static boolean isNum(String num) {
		if (isEmpty(num))
			return false;
		Pattern pattern = Pattern.compile("^\\d+$");
		Matcher isNum = pattern.matcher(num);
		return isNum.matches();
	}
	
	/**
	 * 校验字符串是否匹配正则表达式
	 * @param checkStr
	 * @param regex
	 * @return
	 */
	public static boolean isMatche(String checkStr, String regex) {

		if (checkStr == null || checkStr.equals(""))
			return false;

		Pattern pattern = Pattern.compile(regex);
		Matcher isMathe = pattern.matcher(checkStr);
		return isMathe.matches();
	}

	/**
	 * 判断是否为浮点数
	 * @param num
	 * @return
	 */
	public static boolean isFloat(String num) {
		if (isNum(num))
			return true;
		String reg = "^[1-9]\\d*\\.\\d*|0\\.\\d*[0-9]\\d*$";

		if (num == null || num.equals(""))
			return false;

		if (num.startsWith("-")) {
			return isMatche(num.substring(1), reg);
		}
		return isMatche(num, reg);
	}
	
	/**
	 * 可以用于判断字符串是否为包含关系
	 * @param str:源字符串
	 * @param subStr：子字符串
	 * @param splitFlag:源字符串的分隔标志
	 * @return
	 */
	public static boolean isIndexOf(String str, String subStr, String splitFlag) {
		if (isEmpty(str)) {
			return false;
		}
		// 子字符串为空,认为包含
		if (isEmpty(subStr)) {
			return true;
		}
		if (null == splitFlag) {
			splitFlag = "";
		}
		String tmpStr = splitFlag + str + splitFlag;
		String tmpSubStr = splitFlag + subStr + splitFlag;
		return tmpStr.indexOf(tmpSubStr) >= 0;
	}

	/**
     * 判断字符串中是否包含中文
     * @param str
     * 待校验字符串
     * @return 是否为中文
     * @warn 不能校验是否为中文标点符号
     */
    public static boolean isContainChinese(String str) {
        Pattern p = Pattern.compile("[\u4e00-\u9fa5]");
        Matcher m = p.matcher(str);
        if (m.find()) {
            return true;
        }
        return false;
    }
    
	/**
	 * 主函数.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		//System.out.println(join("-", "asdf", "1", "2", "3"));
		System.out.println(isFloat("0.23"));
		System.out.println(isFloat("23"));
		System.out.println(isContainChinese("23"));
		System.out.println(isContainChinese("23AS23"));
		System.out.println(isContainChinese("324234站**"));
		String msg = "sysCode|subSysCode|sysService|logType|host|node|dateTime|alpMessageTime|uniqCode|ext|split|logConfig";
		System.out.print(msg.indexOf("|", 1));
	}
}
