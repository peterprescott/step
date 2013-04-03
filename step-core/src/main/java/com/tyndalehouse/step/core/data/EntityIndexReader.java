package com.tyndalehouse.step.core.data;

import java.io.Closeable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;

/**
 * Interface to read an index
 * 
 * @author chrisburrell
 * 
 */
public interface EntityIndexReader extends Closeable {

    /**
     * @return the appropriate analyzer
     */
    Analyzer getAnalyzer();

    /**
     * Refreshes the index after it has been created.
     */
    void refresh();

    /**
     * closes without throwing an exception
     */
    void close();

    /**
     * Searches by a single field for multiple values, expecting each value to return just one result
     * 
     * @param fieldName the field name
     * @param values the list of values to be retrieved
     * @param max the number of results to return
     * @return the scored documents
     */
    EntityDoc[] searchExactTermBySingleField(String fieldName, int max, String... values);

    /**
     * Searches by a single field for multiple values, expecting each value to return just one result
     * 
     * @param fieldName the field name
     * @param values the list of values to be retrieved
     * @return the scored documents
     */
    EntityDoc[] searchUniqueBySingleField(String fieldName, String... values);

    /**
     * Searches for a particular value across multiple fields
     * 
     * @param fieldNames the names of all fields to be searched
     * @param value the value to be searched for
     * @param filter the filter, possibly null
     * @param sort the sort, possibly null
     * @param analyzePrefix true to use an analyzer on the prefix
     * @return the expected results
     */
    EntityDoc[] search(String[] fieldNames, String value, Filter filter, Sort sort, boolean analyzePrefix);

    /**
     * Searches for a particular value across multiple fields
     * 
     * @param fieldNames the names of all fields to be searched
     * @param value the value to be searched for
     * @param filter the filter, possibly null
     * @param sort the sort, possibly null
     * @param analyzePrefix true to use an analyzer on the prefix
     * @param queryRemainder an extra bit to add to the query
     * @return the expected results
     */
    EntityDoc[] search(String[] fieldNames, String value, Filter filter, Sort sort, boolean analyzePrefix,
            String queryRemainder);

    /**
     * Searches for a particular value across multiple fields
     * 
     * @param fieldNames the names of all fields to be searched
     * @param value the value to be searched for
     * @param filter the filter, possibly null
     * @param sort the sort, possibly null
     * @param analyzePrefix true to use an analyzer on the prefix
     * @param maxResults the maximum number of results
     * @return the expected results
     */
    EntityDoc[] search(String[] fieldNames, String value, Filter filter, Sort sort, boolean analyzePrefix,
            Integer maxResults);

    /**
     * Searches for a particular value across multiple fields
     * 
     * @param fieldNames the names of all fields to be searched
     * @param value the value to be searched for
     * @param filter the filter, possibly null
     * @param sort the sort, possibly null
     * @param analyzePrefix true to use an analyzer on the prefix
     * @param queryRemainder an extra bit to add to the query
     * @param maxResults the maximum number of results
     * @return the expected results
     */
    EntityDoc[] search(String[] fieldNames, String value, Filter filter, Sort sort, boolean analyzePrefix,
            String queryRemainder, Integer maxResults);

    /**
     * Searches for all documents given by a query
     * 
     * @param query the query
     * @return the list of all docs
     */
    EntityDoc[] search(Query query);

    /**
     * Searches with a given query
     * 
     * @param query the query
     * @param max the max number of results
     * @param sortField the field to sort by
     * @return the entity documents that have been found
     */
    EntityDoc[] search(Query query, int max, Sort sortField);

    /**
     * Allows all kinds of queries, but on one column only
     * 
     * @param fieldName the name of the field to search for
     * @param querySyntax the query syntax, can contain wildcards...
     * @param op the default operator
     * @param allowLeadingWildcard true to allow leading wildcards
     * @return all matched results
     */
    EntityDoc[] searchSingleColumn(String fieldName, String querySyntax, Operator op,
            boolean allowLeadingWildcard);

    /**
     * Allows all kinds of queries, but on one column only
     * 
     * @param fieldName the name of the field to search for
     * @param querySyntax the query syntax, can contain wildcards...
     * @return a list of matching entity documents
     */
    EntityDoc[] searchSingleColumn(String fieldName, String querySyntax);

}