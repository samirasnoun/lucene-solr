package org.apache.lucene.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.lucene.util.LuceneTestCase;

/**
 * Analyzer for testing
 * <p>
 * This analyzer is a replacement for Whitespace/Simple/KeywordAnalyzers
 * for unit tests. If you are testing a custom component such as a queryparser
 * or analyzer-wrapper that consumes analysis streams, its a great idea to test
 * it with this analyzer instead. MockAnalyzer has the following behavior:
 * <ul>
 *   <li>By default, the assertions in {@link MockTokenizer} are turned on for extra
 *       checks that the consumer is consuming properly. These checks can be disabled
 *       with {@link #setEnableChecks(boolean)}.
 *   <li>Payload data is randomly injected into the stream for more thorough testing
 *       of payloads.
 * </ul>
 * @see MockTokenizer
 */
public final class MockAnalyzer extends Analyzer { 
  private final int pattern;
  private final boolean lowerCase;
  private final CharArraySet filter;
  private final boolean enablePositionIncrements;
  private int positionIncrementGap;
  private final Random random;
  private Map<String,Integer> previousMappings = new HashMap<String,Integer>();
  private boolean enableChecks = true;

  /**
   * Creates a new MockAnalyzer.
   * 
   * @param random Random for payloads behavior
   * @param pattern pattern constant describing how tokenization should happen
   * @param lowerCase true if the tokenizer should lowercase terms
   * @param filter CharArraySet describing how terms should be filtered (set of stopwords, etc)
   * @param enablePositionIncrements true if position increments should reflect filtered terms.
   */
  public MockAnalyzer(Random random, int pattern, boolean lowerCase, CharArraySet filter, boolean enablePositionIncrements) {
    this.random = random;
    this.pattern = pattern;
    this.lowerCase = lowerCase;
    this.filter = filter;
    this.enablePositionIncrements = enablePositionIncrements;
  }

  /**
   * Calls {@link #MockAnalyzer(Random, int, boolean, CharArraySet, boolean) 
   * MockAnalyzer(random, pattern, lowerCase, CharArraySet.EMPTY_STOPSET, false}).
   */
  public MockAnalyzer(Random random, int pattern, boolean lowerCase) {
    this(random, pattern, lowerCase, CharArraySet.EMPTY_SET, false);
  }

  /** 
   * Create a Whitespace-lowercasing analyzer with no stopwords removal.
   * <p>
   * Calls {@link #MockAnalyzer(Random, int, boolean) 
   * MockAnalyzer(random, MockTokenizer.WHITESPACE, true)}.
   */
  public MockAnalyzer(Random random) {
    this(random, MockTokenizer.WHITESPACE, true);
  }

  @Override
  public TokenStream tokenStream(String fieldName, Reader reader) {
    MockTokenizer tokenizer = new MockTokenizer(reader, pattern, lowerCase);
    tokenizer.setEnableChecks(enableChecks);
    StopFilter filt = new StopFilter(LuceneTestCase.TEST_VERSION_CURRENT, tokenizer, filter);
    filt.setEnablePositionIncrements(enablePositionIncrements);
    return maybePayload(filt, fieldName);
  }

  private class SavedStreams {
    MockTokenizer tokenizer;
    TokenFilter filter;
  }

  @Override
  public TokenStream reusableTokenStream(String fieldName, Reader reader)
      throws IOException {
    @SuppressWarnings("unchecked") Map<String,SavedStreams> map = (Map) getPreviousTokenStream();
    if (map == null) {
      map = new HashMap<String,SavedStreams>();
      setPreviousTokenStream(map);
    }
    
    SavedStreams saved = map.get(fieldName);
    if (saved == null) {
      saved = new SavedStreams();
      saved.tokenizer = new MockTokenizer(reader, pattern, lowerCase);
      saved.tokenizer.setEnableChecks(enableChecks);
      StopFilter filt = new StopFilter(LuceneTestCase.TEST_VERSION_CURRENT, saved.tokenizer, filter);
      filt.setEnablePositionIncrements(enablePositionIncrements);
      saved.filter = filt;
      saved.filter = maybePayload(saved.filter, fieldName);
      map.put(fieldName, saved);
      return saved.filter;
    } else {
      saved.tokenizer.reset(reader);
      return saved.filter;
    }
  }
  
  private synchronized TokenFilter maybePayload(TokenFilter stream, String fieldName) {
    Integer val = previousMappings.get(fieldName);
    if (val == null) {
      val = -1; // no payloads
      if (LuceneTestCase.rarely(random)) {
        switch(random.nextInt(3)) {
          case 0: val = -1; // no payloads
                  break;
          case 1: val = Integer.MAX_VALUE; // variable length payload
                  break;
          case 2: val = random.nextInt(12); // fixed length payload
                  break;
        }
      }
      previousMappings.put(fieldName, val); // save it so we are consistent for this field
    }
    
    if (val == -1)
      return stream;
    else if (val == Integer.MAX_VALUE)
      return new MockVariableLengthPayloadFilter(random, stream);
    else
      return new MockFixedLengthPayloadFilter(random, stream, val);
  }
  
  public void setPositionIncrementGap(int positionIncrementGap){
    this.positionIncrementGap = positionIncrementGap;
  }
  
  @Override
  public int getPositionIncrementGap(String fieldName){
    return positionIncrementGap;
  }
  
  /** 
   * Toggle consumer workflow checking: if your test consumes tokenstreams normally you
   * should leave this enabled.
   */
  public void setEnableChecks(boolean enableChecks) {
    this.enableChecks = enableChecks;
  }
}
