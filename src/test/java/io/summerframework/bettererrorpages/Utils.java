package io.summerframework.bettererrorpages;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Created on April, 2019
 *
 * @author destan
 */
class Utils {

	private Utils() {
		throw new UnsupportedOperationException("Cannot be instantiated!");
	}

	static String readSampleFile(String sampleFilePath) {

		try {
			final Resource sampleFileAsResource = new ClassPathResource(sampleFilePath);
			return IOUtils.toString(sampleFileAsResource.getInputStream(), StandardCharsets.UTF_8.name());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static Path getSampleDirPath(String sampleDir) {

		try {
			return new ClassPathResource(sampleDir).getFile().toPath();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static String readSampleFile(Path sampleFilePath) {

		try {
			return new String(Files.readAllBytes(sampleFilePath));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static Method getParseTraceForClassMethod() throws NoSuchMethodException {
		return getTracerMethod("parseTraceForClass");
	}

	static Method getparseTraceForTemplateMethod() throws NoSuchMethodException {
		return getTracerMethod("parseTraceForTemplate");
	}

	private static Method getTracerMethod(String methodName) throws NoSuchMethodException {
		final Method method = TraceParser.class.getDeclaredMethod(methodName, String.class);
		method.setAccessible(true);
		return method;
	}
}
