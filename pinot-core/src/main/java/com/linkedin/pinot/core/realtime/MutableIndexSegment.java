/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.realtime;

import com.linkedin.pinot.core.data.GenericRow;
import com.linkedin.pinot.core.indexsegment.IndexSegment;


public interface MutableIndexSegment extends IndexSegment {

  /**
   * expects a generic row that has all the columns
   * specified in the schema which was used to
   * initialize the realtime segment
   * @param row
   */
  public boolean index(GenericRow row);

  /**
   * gives the raw count of the total number of streaming events
   * that are indexed
   * @return
   */
  int getRawDocumentCount();

  /**
   * gives the aggregate count of the events,
   * in case an implementation is aggregating events
   * raw count will be &gt; aggregate count
   * otherwise
   * raw count will be = aggregate count
   * @return
   */
  int getAggregateDocumentCount();
}
