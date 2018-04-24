/*
 *  Copyright © 2018 Kod Gemisi Ltd.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is “Incompatible With Secondary Licenses”, as defined by
 * the Mozilla Public License, v. 2.0.
 *
 */

package com.kodgemisi.summer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created on April, 2018
 *
 * @author destan
 */
@Slf4j
public class ThymeleafExceptionUtils {

	private static final String SPAN_START = "<span class=\"own-class\">";

	private static final String SPAN_END = "</span>";

	private final String projectPathForJavaFiles;

	private final String projectPathForTemplateFiles;

	private final String packageName;

	/**
	 * matcher.group(0): whole line
	 * matcher.group(1): fully qualified class name
	 * matcher.group(2): package name
	 * matcher.group(3): class name
	 * matcher.group(4): file name
	 * matcher.group(5): line number
	 */
	private final Pattern classNameRegexPattern;

	/**
	 * matcher.group(0): whole line
	 * matcher.group(1): template name
	 * matcher.group(2): line number
	 */
	private final Pattern templateNameRegexPattern = Pattern.compile("Caused by: .*\\(template: \"(.+)\" - line (\\d)+, col .+\\)");

	/**
	 * matcher.group(0): whole line
	 * matcher.group(1): template path
	 */
	private final Pattern templatePathRegexPattern = Pattern.compile("\\(template:.*\\[(.*)\\]\"\\)");// \(template:.*\[(.*)\]"\)

	//Maybe for future use, Last Caused by regex: `"Caused by:(?:.(?!Caused by:))+$"`

	protected ThymeleafExceptionUtils(String projectPath, String packageName) {
		this.projectPathForJavaFiles = projectPath + "src/main/java/";
		this.projectPathForTemplateFiles = projectPath + "src/main/resources/";
		this.packageName = packageName;
		classNameRegexPattern = Pattern.compile("at ((" + this.packageName + "[a-z0-9\\.]*)\\.([A-Z]\\w*)).*\\((.+):(\\d+)\\)");
	}

	private String colorizeTrace(String trace) {
		return String.join("\n", Arrays.stream(trace.split("\n"))
				.map(s -> s.contains(packageName) ? (SPAN_START + s + SPAN_END) : s)
				.toArray(String[]::new));
	}

	public String styledTrace(String trace) {
		if(trace == null) {
			return null;
		}
		return colorizeTrace(trace);
	}

	@NonNull
	public List<ErrorContext> getListOfErrorContext(String trace) {

		if(trace == null) {
			log.warn("Trace is null, this is normal for 404 errors but if error is different and you think there should be a trace please make sure that you have server.error.include-stacktrace=always");
			return Collections.emptyList();
		}

		final List<ErrorContext> errorContexts = getErrorContexts(trace);

		for(ErrorContext errorContext : errorContexts) {
			final String classFileRelativePath = errorContext.getRelativePathOfClass();
			if(log.isTraceEnabled()) {
				log.trace("classFileRelativePath is {}", classFileRelativePath);
			}

			try {
				final String basePath = errorContext.getFileType() == ErrorContext.FileType.HTML ? projectPathForTemplateFiles : projectPathForJavaFiles;
				final Path sourceFilePath = Paths.get(basePath, classFileRelativePath);

				if(log.isTraceEnabled()) {
					log.trace("sourceFilePath is {}", sourceFilePath);
				}

				final int firstLineNumber = Math.max(errorContext.getErrorLineNumber() - 6, 0);
				final int lastLineNumber = errorContext.getErrorLineNumber() + 5;
				final AtomicInteger index = new AtomicInteger();

				final String sourceCode = String.join("\n", Files.readAllLines(sourceFilePath).stream()
						.filter(s -> {
							final int currentLineNumber = index.getAndIncrement();
							return currentLineNumber >= firstLineNumber && currentLineNumber < lastLineNumber;
						})
						.toArray(String[]::new));

				errorContext.setFirstLineNumber(firstLineNumber + 1);
				errorContext.setSourceCode(sourceCode);
				errorContext.setSourceCodePath(sourceFilePath.toString());
			}
			catch (IOException e) {
				log.error(e.getMessage(), e);
				errorContext.setSourceCode("Cannot parse source file, exception is logged.");
			}
		}
		return errorContexts;
	}

	private List<ErrorContext> getErrorContexts(String trace) {
		final Matcher matcher = classNameRegexPattern.matcher(trace);
		List<ErrorContext> errorContexts = new ArrayList<>();
		while(matcher.find() && matcher.groupCount() > 0) {
			errorContexts.add(ErrorContext.extractFromClassMatcher(matcher));
		}

		if(errorContexts.isEmpty()) {
			final Matcher matcherForTemplateMeta = templateNameRegexPattern.matcher(trace);
			final Matcher matcherForTemplatePath = templatePathRegexPattern.matcher(trace);
			while(matcherForTemplateMeta.find() && matcherForTemplateMeta.groupCount() > 0) {
				errorContexts.add(ErrorContext.extractFromTemplateMatcher(matcherForTemplateMeta, matcherForTemplatePath));
				break;//No need to parse the rest as they will yield the same file name for template errors
			}
		}

		return errorContexts;
	}

	@Getter
	private static class ErrorContext {

		enum FileType {
			JAVA, HTML
		}

		private String fullyQualifiedClassName;

		private String packageName;

		private String className;

		/**
		 * For inner classes and non-public classes their file name is different
		 */
		private String fileName;

		private int errorLineNumber;

		@Setter
		private String sourceCodePath;

		@Setter
		private String sourceCode;

		@Setter
		private int firstLineNumber;

		private final FileType fileType;

		public String getId() {
			return fullyQualifiedClassName + ":" + errorLineNumber;
		}

		private ErrorContext(String templateName, String errorLineNumber, @Nullable String templateFullPath) {
			this.errorLineNumber = Integer.parseInt(errorLineNumber);

			this.packageName = "";
			this.fileName = templateFullPath != null ? templateFullPath : "templates/" + templateName + ".html";
			this.className = fileName;
			this.fullyQualifiedClassName = this.fileName;
			fileType = FileType.HTML;
		}

		private ErrorContext(String fullyQualifiedClassName, String packageName, String className, String fileName, String errorLineNumber) {
			this.fullyQualifiedClassName = fullyQualifiedClassName;
			this.packageName = packageName;
			this.className = className;
			this.fileName = fileName;
			this.errorLineNumber = Integer.parseInt(errorLineNumber);
			fileType = FileType.JAVA;
		}


		/**
		 * <pre>
		 * matcher.group(0): whole line like at com.kodgemisi.demo.ExampleController.error(ExampleController.java:69)
		 * matcher.group(1): fully qualified class name like like com.kodgemisi.demo.ExampleController
		 * matcher.group(2): package name like com.kodgemisi.demo
		 * matcher.group(3): class name like ExampleController
		 * matcher.group(4): file name like ExampleController.java
		 * matcher.group(5): line number like 69
		 * </pre>
		 */
		static ErrorContext extractFromClassMatcher(Matcher matcher) {
			return new ErrorContext(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
		}

		static ErrorContext extractFromTemplateMatcher(Matcher matcher, Matcher matcherForTemplatePath) {
			final String templateFullPath = matcherForTemplatePath.find() && matcherForTemplatePath.groupCount() > 0 ? matcherForTemplatePath.group(1) : null;
			return new ErrorContext(matcher.group(1), matcher.group(2), templateFullPath);
		}

		String getRelativePathOfClass() {
			return this.getPackageName().replaceAll("\\.", File.separator) + File.separator + this.getFileName();
		}
	}
}
