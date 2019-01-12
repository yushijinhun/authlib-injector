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
package moe.yushi.authlibinjector.internal.org.json.simple.parser;

import java.io.IOException;

/**
 * A simplified and stoppable SAX-like content handler for stream processing of JSON text.
 *
 * @see org.xml.sax.ContentHandler
 * @see moe.yushi.authlibinjector.internal.org.json.simple.parser.JSONParser#parse(java.io.Reader, ContentHandler, boolean)
 *
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public interface ContentHandler {
	/**
	 * Receive notification of the beginning of JSON processing.
	 * The parser will invoke this method only once.
	 *
	 * @throws ParseException
	 *             - JSONParser will stop and throw the same exception to the caller when receiving this exception.
	 */
	void startJSON() throws ParseException, IOException;

	/**
	 * Receive notification of the end of JSON processing.
	 *
	 * @throws ParseException
	 */
	void endJSON() throws ParseException, IOException;

	/**
	 * Receive notification of the beginning of a JSON object.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *             - JSONParser will stop and throw the same exception to the caller when receiving this exception.
	 * @see #endJSON
	 */
	boolean startObject() throws ParseException, IOException;

	/**
	 * Receive notification of the end of a JSON object.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *
	 * @see #startObject
	 */
	boolean endObject() throws ParseException, IOException;

	/**
	 * Receive notification of the beginning of a JSON object entry.
	 *
	 * @param key
	 *            - Key of a JSON object entry.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *
	 * @see #endObjectEntry
	 */
	boolean startObjectEntry(String key) throws ParseException, IOException;

	/**
	 * Receive notification of the end of the value of previous object entry.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *
	 * @see #startObjectEntry
	 */
	boolean endObjectEntry() throws ParseException, IOException;

	/**
	 * Receive notification of the beginning of a JSON array.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *
	 * @see #endArray
	 */
	boolean startArray() throws ParseException, IOException;

	/**
	 * Receive notification of the end of a JSON array.
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 *
	 * @see #startArray
	 */
	boolean endArray() throws ParseException, IOException;

	/**
	 * Receive notification of the JSON primitive values:
	 * java.lang.String,
	 * java.lang.Number,
	 * java.lang.Boolean
	 * null
	 *
	 * @param value
	 *            - Instance of the following:
	 *            java.lang.String,
	 *            java.lang.Number,
	 *            java.lang.Boolean
	 *            null
	 *
	 * @return false if the handler wants to stop parsing after return.
	 * @throws ParseException
	 */
	boolean primitive(Object value) throws ParseException, IOException;

}
