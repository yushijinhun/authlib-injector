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
 * $Id: Yytoken.java,v 1.1 2006/04/15 14:10:48 platform Exp $
 * Created on 2006-4-15
 */
package moe.yushi.authlibinjector.internal.org.json.simple.parser;

/**
 * @author FangYidong<fangyidong@yahoo.com.cn>
 */
class Yytoken {
	public static final int TYPE_VALUE = 0;// JSON primitive value: string,number,boolean,null
	public static final int TYPE_LEFT_BRACE = 1;
	public static final int TYPE_RIGHT_BRACE = 2;
	public static final int TYPE_LEFT_SQUARE = 3;
	public static final int TYPE_RIGHT_SQUARE = 4;
	public static final int TYPE_COMMA = 5;
	public static final int TYPE_COLON = 6;
	public static final int TYPE_EOF = -1;// end of file

	public int type = 0;
	public Object value = null;

	public Yytoken(int type, Object value) {
		this.type = type;
		this.value = value;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		switch (type) {
			case TYPE_VALUE:
				sb.append("VALUE(").append(value).append(")");
				break;
			case TYPE_LEFT_BRACE:
				sb.append("LEFT BRACE({)");
				break;
			case TYPE_RIGHT_BRACE:
				sb.append("RIGHT BRACE(})");
				break;
			case TYPE_LEFT_SQUARE:
				sb.append("LEFT SQUARE([)");
				break;
			case TYPE_RIGHT_SQUARE:
				sb.append("RIGHT SQUARE(])");
				break;
			case TYPE_COMMA:
				sb.append("COMMA(,)");
				break;
			case TYPE_COLON:
				sb.append("COLON(:)");
				break;
			case TYPE_EOF:
				sb.append("END OF FILE");
				break;
		}
		return sb.toString();
	}
}
