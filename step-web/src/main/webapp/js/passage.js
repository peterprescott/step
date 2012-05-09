/*******************************************************************************
 * Copyright (c) 2012, Directors of the Tyndale STEP Project
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * Neither the name of the Tyndale House, Cambridge (www.TyndaleHouse.com)  
 * nor the names of its contributors may be used to endorse or promote 
 * products derived from this software without specific prior written 
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

/**
 * Definition of the Passage component responsible for displaying OSIS passages
 * appropriately. Ties the search box for the reference and the version together
 * to the passage displayed
 * 
 * @param passageContainer
 *            the passage Container containing the whole control
 * @param versions
 *            the list of versions to use to populate the dropdown
 */
function Passage(passageContainer, rawServerVersions, passageId) {
	var self = this;
	this.container = passageContainer;
	this.version = $(".passageVersion", passageContainer);
	this.reference = $(".passageReference", passageContainer);
	this.passage = $(".passageContent", passageContainer);
	this.bookmarkButton = $(".bookmarkPassageLink", passageContainer);
	this.previousChapter = $(".previousChapter", passageContainer);
	this.nextChapter = $(".nextChapter", passageContainer);
	
	this.passageId = passageId;
	this.passageSync = false;
	
	// read state from the cookie
	this.setInitialPassage();
	
	this.initVersionsTextBox(rawServerVersions);
	this.initReferenceTextBox();
	
	//this is so that when we click a word, it highlights it
	this.passage.click(function(e) {
		var clickedWord = getWordAtPoint(this, e.pageX, e.pageY);
		var lookup = clickedWord.replace(/[ ,.;:"]/g, "");
		
		$.shout("show-all-strong-morphs", { displayedWord: lookup } );
		
	});
	
	
	
	// register to listen for events that click a word/phrase:
	this.passage.hear("show-all-strong-morphs", function(selfElement, data) {
		self.higlightStrongs(data);
	});
	
	// register we want to be notified of menu option changes...
	this.passage.hear("toolbar-menu-options-changed-" + this.passageId, function(selfElement, data) {
			self.changePassage();
	});

	// register when we want to be alerted that a bookmark has changed
	this.passage.hear("new-passage-" + this.passageId, function(selfElement, data) {
		self.reference.val(data);
		self.changePassage();
	});

	// register when we want to be alerted that a bookmark has changed
	this.passage.hear("show-preview-" + this.passageId, function(selfElement, previewData) {
		self.showPreview(previewData);
	});
	
	
	this.passage.hear("version-list-refresh", function(selfElement, versions) {
		self.refreshVersionsTextBox(versions);
	});
	
	this.bookmarkButton.hear("bookmark-passage-" + this.passageId, function(selfElement, data) {
		self.bookmarkButton.click();
	});
	
	this.passage.hear("sync-passage-activated", function(selfElement, data) {
		self.doSync();
	});
	
	this.passage.hear("sync-passage-deactivated", function(selfElement, data) {
		self.deSync();
	});

	this.bookmarkButton
		.button({ icons: {primary: "ui-icon-bookmark" }, text: false})
		.click(function() {
			$.shout("bookmark-addition-requested", { reference: self.reference.val() });
	});
	

	this.previousChapter
		.button({ icons: {primary: "ui-icon-arrowreturnthick-1-w" }, text: false})
		.click(function() {
			$.getSafe(BIBLE_GET_PREVIOUS_CHAPTER + self.reference.val() + "/" + self.version.val(), function(newReference) {
				self.changePassage(newReference);
			});
	});

	this.nextChapter
		.button({ icons: {primary: "ui-icon-arrowreturnthick-1-w" }, text: false})
		.click(function() {
			$.getSafe(BIBLE_GET_NEXT_CHAPTER + self.reference.val() + "/" + self.version.val(), function(newReference) {
				self.changePassage(newReference);
			});
	});
	
};



/**
 * refreshes the list attached to the version dropdown
 */
Passage.prototype.refreshVersionsTextBox = function(rawServerVersions) {
	// need to make server response adequate for autocomplete:
	var parsedVersions = $.map(rawServerVersions, function(item) {
		var showingText = "[" + item.initials + "] " + item.name;
		var features = "";
		// add to Strongs if applicable, and therefore interlinear
		if(item.hasStrongs) {
			features += " " + "<span class='versionFeature strongsFeature' title='Supports Strongs concordance'>V</span>";
			features += " " + "<span class='versionFeature interlinearFeature' title='Supports interlinear feature'>I</span>";
		}

		// add morphology
		if(item.hasMorphology) {
			features += " " + "<span class='versionFeature morphologyFeature' title='Supports the grammar feature'>G</span>";
		}
		
		// return response for dropdowns
		return {
			label : showingText,
			value : item.initials,
			features: features
		};
	});
	
	this.version.autocomplete({source: parsedVersions});
};

/**
 * Sets up the autocomplete for the versions dropdown
 */
Passage.prototype.initVersionsTextBox = function(rawServerVersions) {
	var self = this;
	
	// set up autocomplete
	this.version.autocomplete({
		minLength: 0,
		delay: 0,
		select : function(event, ui) {
			$(this).val(ui.item.value);
			$(this).change();
		},
	}).focus(function() {
		self.version.autocomplete("search", "");
	}).change(function() {
		$.shout("version-changed-" + self.passageId, this.value);
	});
	
	this.version.data( "autocomplete" )._renderItem = function( ul, item ) {
		return $( "<li></li>" )
		.data( "item.autocomplete", item )
		.append( "<a><span class='features'>" + item.features + "</span>" + item.label + "</a>")
		.appendTo( ul );
	};
	
	this.refreshVersionsTextBox(rawServerVersions);
};

Passage.prototype.initReferenceTextBox = function() {	
	var self = this;
	
	// set up change for textbox
	this.reference.autocomplete({
		source : function(request, response) {
			$.get(BIBLE_GET_BIBLE_BOOK_NAMES + request.term + "/" + self.version.val(), function(text) {
				response(text);
			});
		},
		minLength: 0,
		delay: 0,
		select : function(event, ui) {
			$(this).val(ui.item.value);
		}
	}).change(function(){
		self.changePassage();
	});
};


/**
 * sets up the initial passages based on the cookie state
 */
Passage.prototype.setInitialPassage = function() {
	var cookieReference = $.cookie("currentReference-" + this.passageId);
	var cookieVersion = $.cookie("currentVersion-" + this.passageId);
	if(cookieReference != null) {
		this.reference.val(cookieReference);
	}
	
	if(cookieVersion != null) {
		this.version.val(cookieVersion);
	}
};

/**
 * We are forcing a passage sync, which means that we want to change the passage
 * reference text to match passage-0
 */
Passage.prototype.doSync = function() {
	var self = this;
	if(this.passageId != 0) {
		this.passageSync = true;
		this.reference.attr("disabled", "disabled");
		this.reference.attr("title", "To view a separate passage on this side of the screen, " +
				"please use the Options menu and disable the 'Sync both passages' option.");
		this.changePassage();
		
		// set up hearer for all new changes
		this.passage.hear("passage-changed", function(selfElement, data) {
			if(data.passageId == 0) {
				self.changePassage();
			}
		});
	}
};

/**
 * removes the syncing setting
 */
Passage.prototype.deSync = function() {
	if(this.passageId != 0) {
		this.passageSync = false;
		this.reference.removeAttr("disabled");
		this.reference.removeAttr("title");
		this.changePassage();
		
		// unregister hearer
		this.passage.unhear("passage-changed");
	}
};

/**
 * changes the passage, with optional parameters
 */
Passage.prototype.changePassage = function(newReference) {
    if(newReference) {
    	this.reference.val(newReference);
    }
	
	
	// now get the options from toolbar
	var options = this.getSelectedOptions();
	var interlinearVersion = this.getSelectedInterlinearVersion();
	
	var self = this;
	var lookupVersion = this.version.val();
	var lookupReference = this.passageSync ?  $(".passageReference").first().val() : this.reference.val();
	
	if(lookupReference && lookupVersion 
			&& lookupVersion != "" && lookupReference != ""
			&& (   lookupVersion != $.cookie("currentVersion-" + this.passageId) 
				|| lookupReference != $.cookie("currentReference-" + this.passageId)
			    || interlinearVersion != $.cookie("currentInterlinearVersion-" + this.passageId)
				|| !compare(options, this.currentOptions)) 
		) {
		var url = BIBLE_GET_BIBLE_TEXT + lookupVersion + "/" + lookupReference;
		
		if(options && options.length != 0) {
			url += "/" + options ;

			if(interlinearVersion && interlinearVersion.length != 0) {
				url += "/" + interlinearVersion;
			}
		}
		
		// send to server
		$.get(url, function (text) {
			// we get html back, so we insert into passage:
			$.cookie("currentReference-" + self.passageId, lookupReference);
			$.cookie("currentVersion-" + self.passageId, lookupVersion);
			$.cookie("currentOptions-" + self.passageId, options);
			$.cookie("currentInterlinearVersion-" + self.passageId, interlinearVersion);

			// TODO remove completely in favour of cookie storage only
			self.currentOptions = options;
			self.passage.html(text.value);
			
			// passage change was successful, so we let the rest of the UI know
			$.shout("passage-changed", { reference: self.reference.val(), passageId: self.passageId, init: init, version:  lookupVersion} );
		});
	}
};


/**
 * highlights all strongs match parameter strongReference
 * 
 * @strongReference the reference look for across this passage pane and
 *                  highlight
 */
Passage.prototype.highlightStrong = function(strongReference) {
	// check for black listed strongs
	if($.inArray(strongReference, Passage.getBlackListedStrongs()) == -1) {
		$(".verse span[onclick*='" + strongReference + "']", this.container).css("text-decoration", "underline");
		$("span.w[onclick*='" + strongReference + "'] span.text", this.container).css("text-decoration", "underline");
	}
};

/**
 * This method scans the currently selected options in the menu to find out what
 * is selected and what is not...
 */
Passage.prototype.getSelectedOptions = function() {
	var selectedOptions = [];
	// we select all ticks, but only enabled
	$(".innerMenu a:has(img.selectingTick)", this.container).not(".disabled").each(function(index, value) {
		selectedOptions.push(value.name);
	});
	return selectedOptions;
};


Passage.prototype.getSelectedInterlinearVersion = function() {
	// look for menu item for interlinears...
	// we check that it has a tick and is enabled for the INTERLINEAR name
	var menuItem = $("a:has(img.selectingTick)[name = 'INTERLINEAR']", this.container).not(".disabled");
	
	if(menuItem.length) {
		// lookup the only link we have which is the passage-id attribute on the
		// container
		var passageId = $(this.container).attr("passage-id");
		return $(".interlinearPopup[passage-id = '" + passageId + "'] > .interlinearVersions").val();
	}
	return "";
};

/**
 * if a number of strongs are given, separated by a space, highlights all of
 * them
 * 
 * @param strongMorphReference
 *            the references of all strongs and morphs asked for
 */
Passage.prototype.higlightStrongs = function(strongMorphReference) {
	if(strongMorphReference.strong == null) {
		return;
	}
	
	var references = strongMorphReference.strong.split();
	
	// reset all spans that are underlined:
	$(".verse span", this.container).css("text-decoration", "none");
	$("span.text", this.container).css("text-decoration", "none");
	
	for(var ii = 0; ii < references.length; ii++) {
			this.highlightStrong(references[ii]);
	}
};

/**
 * shows a preview of the current text desired
 */
Passage.prototype.showPreview = function(previewData) {
	var reference = previewData.reference;
	var source = previewData.source;

	var myAnchor = this.passageId == 0 ? "left" : "right";
	var offset = (80 * (this.passageId == 0 ? 1 : -1)) + " 0";
	
	$.getSafe(BIBLE_GET_BIBLE_TEXT + this.version.val() + "/" + reference, function(data) {
		$("#popupText").html(data.value + "<span class='previewReferenceKey'>[" + data.reference + "]</span>");

		var popup = $("#previewReference");
		popup.show().position({
			of: $(source),
			my: myAnchor + " center",
			at: "center " + "center",
			offset: offset,
			collision: "fit"
		}).on("mouseleave.previewscripture", function(s) {
			popup.hide();
		});
		
		$(".notesPane").on("mouseleave.previewscripture", function(s) {
			popup.hide();
		});
	});
};
/**
 * static method that returns strongs that should not be tagged in the UI
 */
Passage.getBlackListedStrongs = function() {
	return ["strong:G3588"];
};


/**
 * sets the toolbar so that the passage can open/close it
 */
Passage.prototype.setToolbar = function(toolbar) {
	this.toolbar = toolbar;
};

/**
 * sets the passage container, so that others can insert themselves into it
 */
Passage.prototype.getPassageContainer = function() {
	return this.container;
};

/**
 * @return the reference text
 */
Passage.prototype.getReference = function() {
	return this.reference.val();
};
