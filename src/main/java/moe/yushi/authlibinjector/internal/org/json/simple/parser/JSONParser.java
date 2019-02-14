/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
/*
 * Copyright 2014 FangYidong<fangyidong@yahoo.com.cn>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: JSONParser.java,v 1.1 2006/04/15 14:10:48 platform Exp $
 * Created on 2006-4-15
 */
package moe.yushi.authlibinjector.internal.org.json.simple.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

/**
 * Parser for JSON text. Please note that JSONParser is NOT thread-safe.
 *
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONParser {
	public static final int S_INIT = 0;
	public static final int S_IN_FINISHED_VALUE = 1;// string,number,boolean,null,object,array
	public static final int S_IN_OBJECT = 2;
	public static final int S_IN_ARRAY = 3;
	public static final int S_PASSED_PAIR_KEY = 4;
	public static final int S_IN_ERROR = -1;

	private Yylex lexer = new Yylex((Reader) null);
	private Yytoken token = null;
	private int status = S_INIT;

	private int peekStatus(LinkedList<Integer> statusStack) {
		if (statusStack.size() == 0)
			return -1;
		Integer status = statusStack.getFirst();
		return status.intValue();
	}

	/**
	 * Reset the parser to the initial state without resetting the underlying reader.
	 *
	 */
	public void reset() {
		token = null;
		status = S_INIT;
	}

	/**
	 * Reset the parser to the initial state with a new character reader.
	 *
	 * @param in
	 *            - The new character reader.
	 * @throws IOException
	 * @throws ParseException
	 */
	public void reset(Reader in) {
		lexer.yyreset(in);
		reset();
	}

	/**
	 * @return The position of the beginning of the current token.
	 */
	public int getPosition() {
		return lexer.getPosition();
	}

	public Object parse(String s) throws ParseException {
		return parse(s, (ContainerFactory) null);
	}

	public Object parse(String s, ContainerFactory containerFactory) throws ParseException {
		StringReader in = new StringReader(s);
		try {
			return parse(in, containerFactory);
		} catch (IOException ie) {
			/*
			 * Actually it will never happen.
			 */
			throw new ParseException(-1, ParseException.ERROR_UNEXPECTED_EXCEPTION, ie);
		}
	}

	public Object parse(Reader in) throws IOException, ParseException {
		return parse(in, (ContainerFactory) null);
	}

	/**
	 * Parse JSON text into java object from the input source.
	 *
	 * @param in
	 * @param containerFactory
	 *            - Use this factory to createyour own JSON object and JSON array containers.
	 * @return Instance of the following:
	 *         org.json.simple.JSONObject,
	 *         org.json.simple.JSONArray,
	 *         java.lang.String,
	 *         java.lang.Number,
	 *         java.lang.Boolean,
	 *         null
	 *
	 * @throws IOException
	 * @throws ParseException
	 */
	public Object parse(Reader in, ContainerFactory containerFactory) throws IOException, ParseException {
		reset(in);
		LinkedList<Integer> statusStack = new LinkedList<>();
		LinkedList<Object> valueStack = new LinkedList<>();

		try {
			do {
				nextToken();
				switch (status) {
					case S_INIT: {
						switch (token.type) {
							case Yytoken.TYPE_VALUE: {
								status = S_IN_FINISHED_VALUE;
								statusStack.addFirst(status);
								valueStack.addFirst(token.value);
								break;
							}
							case Yytoken.TYPE_LEFT_BRACE: {
								status = S_IN_OBJECT;
								statusStack.addFirst(status);
								valueStack.addFirst(createObjectContainer(containerFactory));
								break;
							}
							case Yytoken.TYPE_LEFT_SQUARE: {
								status = S_IN_ARRAY;
								statusStack.addFirst(status);
								valueStack.addFirst(createArrayContainer(containerFactory));
								break;
							}
							default:
								status = S_IN_ERROR;
						}// inner switch
						break;
					}

					case S_IN_FINISHED_VALUE: {
						if (token.type == Yytoken.TYPE_EOF)
							return valueStack.removeFirst();
						else
							throw new ParseException(getPosition(), ParseException.ERROR_UNEXPECTED_TOKEN, token);
					}

					case S_IN_OBJECT: {
						switch (token.type) {
							case Yytoken.TYPE_COMMA:
								break;
							case Yytoken.TYPE_VALUE: {
								if (token.value instanceof String) {
									String key = (String) token.value;
									valueStack.addFirst(key);
									status = S_PASSED_PAIR_KEY;
									statusStack.addFirst(status);
								} else {
									status = S_IN_ERROR;
								}
								break;
							}
							case Yytoken.TYPE_RIGHT_BRACE: {
								if (valueStack.size() > 1) {
									statusStack.removeFirst();
									valueStack.removeFirst();
									status = peekStatus(statusStack);
								} else {
									status = S_IN_FINISHED_VALUE;
								}
								break;
							}
							default:
								status = S_IN_ERROR;
								break;
						}// inner switch
						break;
					}

					case S_PASSED_PAIR_KEY: {
						switch (token.type) {
							case Yytoken.TYPE_COLON:
								break;
							case Yytoken.TYPE_VALUE: {
								statusStack.removeFirst();
								String key = (String) valueStack.removeFirst();
								@SuppressWarnings("unchecked")
								Map<String, Object> parent = (Map<String, Object>) valueStack.getFirst();
								parent.put(key, token.value);
								status = peekStatus(statusStack);
								break;
							}
							case Yytoken.TYPE_LEFT_SQUARE: {
								statusStack.removeFirst();
								String key = (String) valueStack.removeFirst();
								@SuppressWarnings("unchecked")
								Map<String, Object> parent = (Map<String, Object>) valueStack.getFirst();
								List<Object> newArray = createArrayContainer(containerFactory);
								parent.put(key, newArray);
								status = S_IN_ARRAY;
								statusStack.addFirst(status);
								valueStack.addFirst(newArray);
								break;
							}
							case Yytoken.TYPE_LEFT_BRACE: {
								statusStack.removeFirst();
								String key = (String) valueStack.removeFirst();
								@SuppressWarnings("unchecked")
								Map<String, Object> parent = (Map<String, Object>) valueStack.getFirst();
								Map<String, Object> newObject = createObjectContainer(containerFactory);
								parent.put(key, newObject);
								status = S_IN_OBJECT;
								statusStack.addFirst(status);
								valueStack.addFirst(newObject);
								break;
							}
							default:
								status = S_IN_ERROR;
						}
						break;
					}

					case S_IN_ARRAY: {
						switch (token.type) {
							case Yytoken.TYPE_COMMA:
								break;
							case Yytoken.TYPE_VALUE: {
								@SuppressWarnings("unchecked")
								List<Object> val = (List<Object>) valueStack.getFirst();
								val.add(token.value);
								break;
							}
							case Yytoken.TYPE_RIGHT_SQUARE: {
								if (valueStack.size() > 1) {
									statusStack.removeFirst();
									valueStack.removeFirst();
									status = peekStatus(statusStack);
								} else {
									status = S_IN_FINISHED_VALUE;
								}
								break;
							}
							case Yytoken.TYPE_LEFT_BRACE: {
								@SuppressWarnings("unchecked")
								List<Object> val = (List<Object>) valueStack.getFirst();
								Map<String, Object> newObject = createObjectContainer(containerFactory);
								val.add(newObject);
								status = S_IN_OBJECT;
								statusStack.addFirst(status);
								valueStack.addFirst(newObject);
								break;
							}
							case Yytoken.TYPE_LEFT_SQUARE: {
								@SuppressWarnings("unchecked")
								List<Object> val = (List<Object>) valueStack.getFirst();
								List<Object> newArray = createArrayContainer(containerFactory);
								val.add(newArray);
								status = S_IN_ARRAY;
								statusStack.addFirst(status);
								valueStack.addFirst(newArray);
								break;
							}
							default:
								status = S_IN_ERROR;
						}// inner switch
						break;
					}
					case S_IN_ERROR:
						throw new ParseException(getPosition(), ParseException.ERROR_UNEXPECTED_TOKEN, token);
				}// switch
				if (status == S_IN_ERROR) {
					throw new ParseException(getPosition(), ParseException.ERROR_UNEXPECTED_TOKEN, token);
				}
			} while (token.type != Yytoken.TYPE_EOF);
		} catch (IOException ie) {
			throw ie;
		}

		throw new ParseException(getPosition(), ParseException.ERROR_UNEXPECTED_TOKEN, token);
	}

	private void nextToken() throws ParseException, IOException {
		token = lexer.yylex();
		if (token == null)
			token = new Yytoken(Yytoken.TYPE_EOF, null);
	}

	private Map<String, Object> createObjectContainer(ContainerFactory containerFactory) {
		if (containerFactory == null)
			return new JSONObject();
		Map<String, Object> m = containerFactory.createObjectContainer();

		if (m == null)
			return new JSONObject();
		return m;
	}

	private List<Object> createArrayContainer(ContainerFactory containerFactory) {
		if (containerFactory == null)
			return new JSONArray();
		List<Object> l = containerFactory.creatArrayContainer();

		if (l == null)
			return new JSONArray();
		return l;
	}
}
