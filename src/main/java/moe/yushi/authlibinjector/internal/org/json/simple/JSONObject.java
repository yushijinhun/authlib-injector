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
 * $Id: JSONObject.java,v 1.1 2006/04/15 14:10:48 platform Exp $
 * Created on 2006-4-10
 */
package moe.yushi.authlibinjector.internal.org.json.simple;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSON object. Key value pairs are unordered. JSONObject supports java.util.Map interface.
 *
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
public class JSONObject extends LinkedHashMap<String, Object> implements JSONAware, JSONStreamAware {

	public JSONObject() {
		super();
	}

	/**
	 * Allows creation of a JSONObject from a Map. After that, both the
	 * generated JSONObject and the Map can be modified independently.
	 *
	 * @param map
	 */
	public JSONObject(Map<String, ?> map) {
		super(map);
	}

	/**
	 * Encode a map into JSON text and write it to out.
	 * If this map is also a JSONAware or JSONStreamAware, JSONAware or JSONStreamAware specific behaviours will be ignored at this top level.
	 *
	 * @see moe.yushi.authlibinjector.internal.org.json.simple.JSONValue#writeJSONString(Object, Writer)
	 *
	 * @param map
	 * @param out
	 */
	public static void writeJSONString(Map<String, ?> map, Writer out) throws IOException {
		if (map == null) {
			out.write("null");
			return;
		}

		boolean first = true;
		Iterator<? extends Map.Entry<String, ?>> iter = map.entrySet().iterator();

		out.write('{');
		while (iter.hasNext()) {
			if (first)
				first = false;
			else
				out.write(',');
			Map.Entry<String, ?> entry = iter.next();
			out.write('\"');
			out.write(JSONValue.escape(entry.getKey()));
			out.write('\"');
			out.write(':');
			JSONValue.writeJSONString(entry.getValue(), out);
		}
		out.write('}');
	}

	@Override
	public void writeJSONString(Writer out) throws IOException {
		writeJSONString(this, out);
	}

	/**
	 * Convert a map to JSON text. The result is a JSON object.
	 * If this map is also a JSONAware, JSONAware specific behaviours will be omitted at this top level.
	 *
	 * @see moe.yushi.authlibinjector.internal.org.json.simple.JSONValue#toJSONString(Object)
	 *
	 * @param map
	 * @return JSON text, or "null" if map is null.
	 */
	public static String toJSONString(Map<String, ?> map) {
		final StringWriter writer = new StringWriter();

		try {
			writeJSONString(map, writer);
			return writer.toString();
		} catch (IOException e) {
			// This should never happen with a StringWriter
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toJSONString() {
		return toJSONString(this);
	}

	@Override
	public String toString() {
		return toJSONString();
	}

	public static String toString(String key, Object value) {
		StringBuffer sb = new StringBuffer();
		sb.append('\"');
		if (key == null)
			sb.append("null");
		else
			JSONValue.escape(key, sb);
		sb.append('\"').append(':');

		sb.append(JSONValue.toJSONString(value));

		return sb.toString();
	}
}
