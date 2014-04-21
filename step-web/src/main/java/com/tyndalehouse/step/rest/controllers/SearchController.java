package com.tyndalehouse.step.rest.controllers;

import com.tyndalehouse.step.core.models.AbstractComplexSearch;
import com.tyndalehouse.step.core.models.LexiconSuggestion;
import com.tyndalehouse.step.core.models.OsisWrapper;
import com.tyndalehouse.step.core.models.SearchToken;
import com.tyndalehouse.step.core.models.SingleSuggestionsSummary;
import com.tyndalehouse.step.core.models.SuggestionsSummary;
import com.tyndalehouse.step.core.models.search.AutoSuggestion;
import com.tyndalehouse.step.core.models.search.PopularSuggestion;
import com.tyndalehouse.step.core.service.BibleInformationService;
import com.tyndalehouse.step.core.service.SearchService;
import com.tyndalehouse.step.core.service.SuggestionService;
import com.tyndalehouse.step.core.service.helpers.SuggestionContext;
import com.tyndalehouse.step.core.service.impl.InternationalRangeServiceImpl;
import com.tyndalehouse.step.core.service.jsword.JSwordPassageService;
import com.tyndalehouse.step.core.service.search.OriginalWordSuggestionService;
import com.tyndalehouse.step.core.service.search.SubjectEntrySearchService;
import com.tyndalehouse.step.core.service.search.SubjectSearchService;
import com.tyndalehouse.step.core.utils.ConversionUtils;
import com.tyndalehouse.step.core.utils.StringUtils;
import com.yammer.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.tyndalehouse.step.core.exceptions.UserExceptionType.APP_MISSING_FIELD;
import static com.tyndalehouse.step.core.utils.StringUtils.isBlank;
import static com.tyndalehouse.step.core.utils.ValidateUtils.notBlank;

/**
 * Caters for searching across the data base
 *
 * @author chrisburrell
 */
@Singleton
public class SearchController {
    private static final Pattern SPLIT_TOKENS = Pattern.compile("\\|");
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);
    private static final String DEFAULT_OPTIONS = "NHVUG";
    private static int MAX_PER_GROUP = 3;
    private final SearchService searchService;
    private final SuggestionService suggestionService;
    private final OriginalWordSuggestionService originalWordSuggestions;
    private final SubjectEntrySearchService subjectEntries;
    private final BibleInformationService bibleInformationService;
    private final InternationalRangeServiceImpl rangeService;
    private SubjectSearchService subjectSearchService;

    /**
     * @param search                  the search service
     * @param originalWordSuggestions the original word suggestions
     * @param subjectEntries          is able to retrieve the search entries
     */
    @Inject
    public SearchController(final SearchService search,
                            final SuggestionService suggestionService,
                            final OriginalWordSuggestionService originalWordSuggestions,
                            final SubjectEntrySearchService subjectEntries,
                            final SubjectSearchService subjectSearchService,
                            final BibleInformationService bibleInformationService,
                            final InternationalRangeServiceImpl rangeService) {
        this.searchService = search;
        this.suggestionService = suggestionService;
        this.originalWordSuggestions = originalWordSuggestions;
        this.subjectEntries = subjectEntries;
        this.subjectSearchService = subjectSearchService;
        this.bibleInformationService = bibleInformationService;
        this.rangeService = rangeService;
    }

    /**
     * Suggests options to the user.
     *
     * @param input the input from the user
     */
    public List<AutoSuggestion> suggest(final String input) {
        return this.suggest(input, null);
    }

    /**
     * Suggests options to the user.
     *
     * @param input   the user input
     * @param context any specific user context, such as the selection of a book, or a particular master version
     *                already in the box
     * @return
     */
    public List<AutoSuggestion> suggest(final String input, final String context) {
        return suggest(input, context, null);
    }

    /**
     * Suggests options to the user.
     *
     * @param input          the input from the user
     * @param context        any specific user context, such as the selection of a book, or a particular master version
     *                       already in the box
     * @param referencesOnly true to indicate we only want references back
     */
    public List<AutoSuggestion> suggest(final String input, final String context, final String referencesOnly) {
        boolean onlyReferences = false;
        if (StringUtils.isNotBlank(referencesOnly)) {
            onlyReferences = Boolean.parseBoolean(referencesOnly);
        }

        final List<AutoSuggestion> autoSuggestions = new ArrayList<AutoSuggestion>(128);
        String bookContext = JSwordPassageService.REFERENCE_BOOK;
        String referenceContext = null;
        String limitType = null;
        boolean exampleData = false;

        if (StringUtils.isNotBlank(context)) {
            //there are some context items... Parse them
            //if there is a reference= restriction, then we will only return references, otherwise, we default
            final List<SearchToken> searchTokens = parseTokens(context);
            for (SearchToken st : searchTokens) {
                if (SearchToken.VERSION.equals(st.getTokenType())) {
                    bookContext = st.getToken();
                } else if (SearchToken.REFERENCE.equals(st.getTokenType())) {
                    referenceContext = st.getToken();
                } else if (SearchToken.LIMIT.equals(st.getTokenType())) {
                    limitType = st.getToken();
                } else if (SearchToken.EXAMPLE_DATA.equals(st.getTokenType())) {
                    exampleData = true;
                }
            }
        }

        if (onlyReferences || referenceContext != null) {
            addReferenceSuggestions(limitType, input, autoSuggestions, bookContext, referenceContext);
        } else {
            addDefaultSuggestions(input, autoSuggestions, limitType, bookContext, exampleData);
        }
        return autoSuggestions;
    }

    /**
     * @param input                the input entered by the user so far
     * @param autoSuggestions      the list of suggestions
     * @param limitType            only one type of data is requested
     * @param referenceBookContext the reference book (i..e master book) that has already been selected by the user.
     * @param exampleData          example data is requested
     */
    private void addDefaultSuggestions(final String input, final List<AutoSuggestion> autoSuggestions, final String limitType, final String referenceBookContext, final boolean exampleData) {
        SuggestionContext context = new SuggestionContext();
        context.setMasterBook(referenceBookContext);
        context.setInput(input);
        context.setSearchType(limitType);
        context.setExampleData(exampleData);

        if (exampleData) {
            convert(autoSuggestions, this.suggestionService.getFirstNSuggestions(context));
        } else if (StringUtils.isBlank(limitType)) {
            // we only return the right set of suggestions if there is a limit type
            convert(autoSuggestions, this.suggestionService.getTopSuggestions(context));
        } else {
            convert(autoSuggestions, this.suggestionService.getFirstNSuggestions(context));
        }
    }

    private void convert(final List<AutoSuggestion> autoSuggestions, final SuggestionsSummary topSuggestions) {
        for (SingleSuggestionsSummary summary : topSuggestions.getSuggestionsSummaries()) {
            //we render each option
            final List<? extends PopularSuggestion> popularSuggestions = summary.getPopularSuggestions();
            for (PopularSuggestion p : popularSuggestions) {
                AutoSuggestion au = new AutoSuggestion();
                au.setItemType(summary.getSearchType().toString());
                au.setSuggestion(p);
                autoSuggestions.add(au);
            }

            if (summary.getMoreResults() > 0 && !SearchToken.REFERENCE.equals(summary.getSearchType())) {
                AutoSuggestion au = new AutoSuggestion();
                au.setItemType(summary.getSearchType().toString());
                au.setGrouped(true);
                au.setCount(summary.getMoreResults());
                au.setMaxReached(SuggestionService.MAX_RESULTS_NON_GROUPED <= summary.getMoreResults());
                au.setExtraExamples(summary.getExtraExamples());
                autoSuggestions.add(au);
            }
        }
    }

    /**
     * Adds the references that match the input
     *
     * @param limitType       limits the types of suggestions to just 1 kind
     * @param input           input from the user
     * @param autoSuggestions the list of suggestions
     * @param version         the version to use in our lookup
     * @param bookScope       the book for which we are looking up chapters
     */
    private void addReferenceSuggestions(final String limitType, final String input, final List<AutoSuggestion> autoSuggestions,
                                         final String version, final String bookScope) {
        addAutoSuggestions(limitType, SearchToken.REFERENCE, autoSuggestions, bibleInformationService.getBibleBookNames(input, version, bookScope));
    }

    /**
     * @param items the list of all items
     */
    public AbstractComplexSearch masterSearch(final String items) {
        return this.masterSearch(items, null, null, null, null, null);
    }

    /**
     * @param items   the list of all items
     * @param options current display options
     */
    public AbstractComplexSearch masterSearch(final String items, final String options) {
        return this.masterSearch(items, options, null, null, null, null);
    }

    /**
     * @param items   the list of all items
     * @param options current display options
     * @param display the display options
     */
    public AbstractComplexSearch masterSearch(final String items, final String options, final String display) {
        return this.masterSearch(items, options, display, null, null, null);
    }

    /**
     * @param items      the list of all items
     * @param options    current display options
     * @param display    the display options
     * @param pageNumber the number of the page that is desired
     */
    public AbstractComplexSearch masterSearch(final String items, final String options, final String display, final String pageNumber) {
        return this.masterSearch(items, options, display, pageNumber, null, null);
    }

    /**
     * @param items      the list of all items
     * @param options    current display options
     * @param display    the display options
     * @param pageNumber the number of the page that is desired
     * @param filter     the type of filter required on an original word search
     */
    public AbstractComplexSearch masterSearch(final String items, final String options, final String display, final String pageNumber, final String filter) {
        return this.masterSearch(items, options, display, pageNumber, filter, null, null);
    }

    /**
     * @param items      the list of all items
     * @param options    current display options
     * @param display    the display options
     * @param pageNumber the number of the page that is desired
     * @param filter     the type of filter required on an original word search
     */
    public AbstractComplexSearch masterSearch(final String items, final String options, final String display, final String pageNumber, final String filter, final String sort) {
        return this.masterSearch(items, options, display, pageNumber, filter, sort, null);
    }
    
    /**
     * @param items      the list of all items
     * @param options    current display options
     * @param display    the display options
     * @param pageNumber the number of the page that is desired
     * @param filter     the type of filter required on an original word search
     * @param context    the amount of context to add to the verses hit by a search
     */
    public AbstractComplexSearch masterSearch(final String items, final String options, final String display,
                                              final String pageNumber, final String filter, final String sortOrder, final String context) {
        final List<SearchToken> searchTokens = parseTokens(items);
        final int page = ConversionUtils.getValidInt(pageNumber, 1);
        final int searchContext = ConversionUtils.getValidInt(context, 0);
        return this.searchService.runQuery(searchTokens, getDefaultedOptions(options), display, page, filter, sortOrder, searchContext, items);
    }

    /**
     * Parses a string in the form of a=2|c=1 into a list of search tokens
     *
     * @param items
     * @return
     */
    private List<SearchToken> parseTokens(final String items) {
        String[] tokens;
        if (!StringUtils.isBlank(items)) {
            tokens = SPLIT_TOKENS.split(items);
        } else {
            tokens = new String[0];
        }

        List<SearchToken> searchTokens = new ArrayList<SearchToken>();
        for (String t : tokens) {
            int indexOfPrefix = t.indexOf('=');
            if (indexOfPrefix == -1) {
                LOGGER.warn("Ignoring item: [{}]", t);
                continue;
            }

            String text = t.substring(indexOfPrefix + 1);
            searchTokens.add(new SearchToken(t.substring(0, indexOfPrefix), text));
        }
        return searchTokens;
    }

    /**
     * @param options if null, returns the default options
     * @return the default options for any passage
     */
    private String getDefaultedOptions(final String options) {
        return options == null ? DEFAULT_OPTIONS : options;
    }

    /**
     * @param autoSuggestions the current suggestions
     * @param suggestions     the list of all suggestions to add
     * @param type            the type of the items
     */
    private void addAutoSuggestions(final String limitType, final String type, final List<AutoSuggestion> autoSuggestions, final List<? extends PopularSuggestion> suggestions) {
        if (StringUtils.isNotBlank(limitType) && !limitType.equals(type)) {
            // we only return the right set of suggestions if there is a limit type
            return;
        }

        //else, we render each option
        for (Object o : suggestions) {
            AutoSuggestion au = new AutoSuggestion();
            au.setItemType(type);
            au.setSuggestion(o);
            autoSuggestions.add(au);
        }
    }


    /**
     * Obtains a list of suggestions to display to the user
     *
     * @param greek   true, to indicate Greek
     * @param form            the form input so far
     * @return a list of suggestions
     */
    @Timed(name = "exact-form-lookup", group = "languages", rateUnit = TimeUnit.SECONDS, durationUnit = TimeUnit.MILLISECONDS)
    public List<LexiconSuggestion> getExactForms(final String form, final String greek) {
        notBlank(form, "Blank lexical prefix passed.", APP_MISSING_FIELD);
        return this.originalWordSuggestions.getExactForms(form, Boolean.parseBoolean(greek));
    }
   
    /**
     * Replaces #plus# and #slash#
     *
     * @param searchQuery the search query
     * @return the string that has replaced
     */
    private String restoreSearchQuery(final String searchQuery) {
        if (isBlank(searchQuery)) {
            return searchQuery;
        }

        return searchQuery.replace("#slash#", "/").replace("#plus#", "+");
    }

    /**
     * @param root       the root word
     * @param fullHeader the header
     * @param version    to be looked up
     * @return the list of verses for this subject
     */
    public List<OsisWrapper> getSubjectVerses(final String root, final String fullHeader, final String version) {
        return this.subjectEntries.getSubjectVerses(root, fullHeader, version);
    }
}
