/*
 * Copyright 2018-2020 Radicalbit S.r.l.
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

package io.radicalbit.nsdb.web

import io.radicalbit.nsdb.model.Schema
import org.json4s.JsonAST.JField
import org.json4s.{CustomSerializer, JObject, JString}

/**
  * Custom serializer for schema.
  */
case object SchemaSerializer
    extends CustomSerializer[Schema](
      _ =>
        (
          {
            case _ =>
              throw new IllegalAccessException(
                "SchemaSerializer can be used only for serialization and not for deserialization")
          }, {
            case schema: Schema =>
              JObject(
                List(
                  JField("value", JString(schema.value.indexType.getClass.getSimpleName)),
                  JField("dimensions", JObject(schema.dimensions.map {
                    case (k, v) => JField(k, JString(v.indexType.getClass.getSimpleName))
                  }.toList)),
                  JField("tags", JObject(schema.tags.map {
                    case (k, v) => JField(k, JString(v.indexType.getClass.getSimpleName))
                  }.toList))
                )
              )
          }
      ))
