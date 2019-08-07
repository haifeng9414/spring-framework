/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.context.request;

import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * {@link WebRequest} adapter for an {@link javax.servlet.http.HttpServletRequest}.
 *
 * @author Juergen Hoeller
 * @author Brian Clozel
 * @author Markus Malkusch
 * @since 2.0
 */
public class ServletWebRequest extends ServletRequestAttributes implements NativeWebRequest {

	private static final String ETAG = "ETag";

	private static final String IF_MODIFIED_SINCE = "If-Modified-Since";

	private static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

	private static final String IF_NONE_MATCH = "If-None-Match";

	private static final String LAST_MODIFIED = "Last-Modified";

	private static final List<String> SAFE_METHODS = Arrays.asList("GET", "HEAD");

	/**
	 * Pattern matching ETag multiple field values in headers such as "If-Match", "If-None-Match"
	 * @see <a href="https://tools.ietf.org/html/rfc7232#section-2.3">Section 2.3 of RFC 7232</a>
	 */
	private static final Pattern ETAG_HEADER_VALUE_PATTERN = Pattern.compile("\\*|\\s*((W\\/)?(\"[^\"]*\"))\\s*,?");

	/**
	 * Date formats as specified in the HTTP RFC
	 * @see <a href="https://tools.ietf.org/html/rfc7231#section-7.1.1.1">Section 7.1.1.1 of RFC 7231</a>
	 */
	private static final String[] DATE_FORMATS = new String[] {
			"EEE, dd MMM yyyy HH:mm:ss zzz",
			"EEE, dd-MMM-yy HH:mm:ss zzz",
			"EEE MMM dd HH:mm:ss yyyy"
	};

	private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	private boolean notModified = false;


	/**
	 * Create a new ServletWebRequest instance for the given request.
	 * @param request current HTTP request
	 */
	public ServletWebRequest(HttpServletRequest request) {
		super(request);
	}

	/**
	 * Create a new ServletWebRequest instance for the given request/response pair.
	 * @param request current HTTP request
	 * @param response current HTTP response (for automatic last-modified handling)
	 */
	public ServletWebRequest(HttpServletRequest request, @Nullable HttpServletResponse response) {
		super(request, response);
	}


	@Override
	public Object getNativeRequest() {
		return getRequest();
	}

	@Override
	public Object getNativeResponse() {
		return getResponse();
	}

	@Override
	public <T> T getNativeRequest(@Nullable Class<T> requiredType) {
		return WebUtils.getNativeRequest(getRequest(), requiredType);
	}

	@Override
	public <T> T getNativeResponse(@Nullable Class<T> requiredType) {
		HttpServletResponse response = getResponse();
		return (response != null ? WebUtils.getNativeResponse(response, requiredType) : null);
	}

	/**
	 * Return the HTTP method of the request.
	 * @since 4.0.2
	 */
	@Nullable
	public HttpMethod getHttpMethod() {
		return HttpMethod.resolve(getRequest().getMethod());
	}

	@Override
	@Nullable
	public String getHeader(String headerName) {
		return getRequest().getHeader(headerName);
	}

	@Override
	@Nullable
	public String[] getHeaderValues(String headerName) {
		String[] headerValues = StringUtils.toStringArray(getRequest().getHeaders(headerName));
		return (!ObjectUtils.isEmpty(headerValues) ? headerValues : null);
	}

	@Override
	public Iterator<String> getHeaderNames() {
		return CollectionUtils.toIterator(getRequest().getHeaderNames());
	}

	@Override
	@Nullable
	public String getParameter(String paramName) {
		return getRequest().getParameter(paramName);
	}

	@Override
	@Nullable
	public String[] getParameterValues(String paramName) {
		return getRequest().getParameterValues(paramName);
	}

	@Override
	public Iterator<String> getParameterNames() {
		return CollectionUtils.toIterator(getRequest().getParameterNames());
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		return getRequest().getParameterMap();
	}

	@Override
	public Locale getLocale() {
		return getRequest().getLocale();
	}

	@Override
	public String getContextPath() {
		return getRequest().getContextPath();
	}

	@Override
	@Nullable
	public String getRemoteUser() {
		return getRequest().getRemoteUser();
	}

	@Override
	@Nullable
	public Principal getUserPrincipal() {
		return getRequest().getUserPrincipal();
	}

	@Override
	public boolean isUserInRole(String role) {
		return getRequest().isUserInRole(role);
	}

	@Override
	public boolean isSecure() {
		return getRequest().isSecure();
	}


	@Override
	public boolean checkNotModified(long lastModifiedTimestamp) {
		return checkNotModified(null, lastModifiedTimestamp);
	}

	@Override
	public boolean checkNotModified(String etag) {
		return checkNotModified(etag, -1);
	}

	@Override
	public boolean checkNotModified(@Nullable String etag, long lastModifiedTimestamp) {
		HttpServletResponse response = getResponse();
		if (this.notModified || (response != null && HttpStatus.OK.value() != response.getStatus())) {
			return this.notModified;
		}

		// Evaluate conditions in order of precedence.
		// See https://tools.ietf.org/html/rfc7232#section-6

		// 判断请求头是否有If-Unmodified-Since，该请求头表示在指定时间之后资源是否没有被修改，下面判断If-Unmodified-Since值是否小于lastModifiedTimestamp
		// 如果是则说明已经被修改了，这里返回412状态码表示Precondition failed（预处理错误），这就说明If-Unmodified-Since的请求头是在文件没有被修改时才下载
		// 这个If-Modified-Since的功能是相反的，If-Modified-Since是在文件被修改时才下载，If-Modified-Since的功能才符合缓存的定义，毕竟没有被修改就没有下载
		// 的必要，而If-Unmodified-Since请求头的作用是用于支持断点续传，不是普通的缓存，断点续传功能当然希望正在下载的文件没有被修改，总结就是
		// If-Unmodified-Since是没修改才下载资源，If-Modified-Since是修改了才下载资源
		if (validateIfUnmodifiedSince(lastModifiedTimestamp)) {
			// validateIfUnmodifiedSince方法会对notModified进行赋值，notModified默认为false，如果If-Unmodified-Since小于lastModifiedTimestamp，也就是
			// 在指定时间之后文件变化了，则notModified为true，看上去在文件变化时应该设置为false，这里设置为true个人认为是因为DispatcherServlet的doDispatch方法
			// 中对checkNotModified方法返回值的处理过程导致的
			if (this.notModified && response != null) {
				response.setStatus(HttpStatus.PRECONDITION_FAILED.value());
			}
			return this.notModified;
		}

		// 验证ETAG请求头，该请求头的值为缓存中资源的版本，这里当ETAG请求头的值和资源的版本相等，则返回true并且notModified被赋值为true
		boolean validated = validateIfNoneMatch(etag);
		// 如果ETAG没有验证过，则验证If-Modified-Since请求头
		if (!validated) {
			// 如果If-Modified-Since指定的时间之内没有被修改，则notModified会被设置为true
			validateIfModifiedSince(lastModifiedTimestamp);
		}

		// Update response
		if (response != null) {
			boolean isHttpGetOrHead = SAFE_METHODS.contains(getRequest().getMethod());
			// 如果文件没有被修改，则根据情况返回状态码
			if (this.notModified) {
				response.setStatus(isHttpGetOrHead ?
						HttpStatus.NOT_MODIFIED.value() : HttpStatus.PRECONDITION_FAILED.value());
			}
			if (isHttpGetOrHead) {
				// 添加Last-Modified请求头
				if (lastModifiedTimestamp > 0 && parseDateValue(response.getHeader(LAST_MODIFIED)) == -1) {
					response.setDateHeader(LAST_MODIFIED, lastModifiedTimestamp);
				}
				// 添加ETAG请求头
				if (StringUtils.hasLength(etag) && response.getHeader(ETAG) == null) {
					response.setHeader(ETAG, padEtagIfNecessary(etag));
				}
			}
		}

		return this.notModified;
	}

	private boolean validateIfUnmodifiedSince(long lastModifiedTimestamp) {
		if (lastModifiedTimestamp < 0) {
			return false;
		}
		long ifUnmodifiedSince = parseDateHeader(IF_UNMODIFIED_SINCE);
		if (ifUnmodifiedSince == -1) {
			return false;
		}
		// We will perform this validation...
		this.notModified = (ifUnmodifiedSince < (lastModifiedTimestamp / 1000 * 1000));
		return true;
	}

	private boolean validateIfNoneMatch(@Nullable String etag) {
		if (!StringUtils.hasLength(etag)) {
			return false;
		}

		Enumeration<String> ifNoneMatch;
		try {
			ifNoneMatch = getRequest().getHeaders(IF_NONE_MATCH);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		if (!ifNoneMatch.hasMoreElements()) {
			return false;
		}

		// We will perform this validation...
		etag = padEtagIfNecessary(etag);
		while (ifNoneMatch.hasMoreElements()) {
			String clientETags = ifNoneMatch.nextElement();
			Matcher etagMatcher = ETAG_HEADER_VALUE_PATTERN.matcher(clientETags);
			// Compare weak/strong ETags as per https://tools.ietf.org/html/rfc7232#section-2.3
			while (etagMatcher.find()) {
				if (StringUtils.hasLength(etagMatcher.group()) &&
						etag.replaceFirst("^W/", "").equals(etagMatcher.group(3))) {
					this.notModified = true;
					break;
				}
			}
		}

		return true;
	}

	private String padEtagIfNecessary(String etag) {
		if (!StringUtils.hasLength(etag)) {
			return etag;
		}
		if ((etag.startsWith("\"") || etag.startsWith("W/\"")) && etag.endsWith("\"")) {
			return etag;
		}
		return "\"" + etag + "\"";
	}

	private boolean validateIfModifiedSince(long lastModifiedTimestamp) {
		if (lastModifiedTimestamp < 0) {
			return false;
		}
		long ifModifiedSince = parseDateHeader(IF_MODIFIED_SINCE);
		if (ifModifiedSince == -1) {
			return false;
		}
		// We will perform this validation...
		this.notModified = ifModifiedSince >= (lastModifiedTimestamp / 1000 * 1000);
		return true;
	}

	public boolean isNotModified() {
		return this.notModified;
	}

	private long parseDateHeader(String headerName) {
		long dateValue = -1;
		try {
			dateValue = getRequest().getDateHeader(headerName);
		}
		catch (IllegalArgumentException ex) {
			String headerValue = getHeader(headerName);
			// Possibly an IE 10 style value: "Wed, 09 Apr 2014 09:57:42 GMT; length=13774"
			if (headerValue != null) {
				int separatorIndex = headerValue.indexOf(';');
				if (separatorIndex != -1) {
					String datePart = headerValue.substring(0, separatorIndex);
					dateValue = parseDateValue(datePart);
				}
			}
		}
		return dateValue;
	}

	private long parseDateValue(@Nullable String headerValue) {
		if (headerValue == null) {
			// No header value sent at all
			return -1;
		}
		if (headerValue.length() >= 3) {
			// Short "0" or "-1" like values are never valid HTTP date headers...
			// Let's only bother with SimpleDateFormat parsing for long enough values.
			for (String dateFormat : DATE_FORMATS) {
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat, Locale.US);
				simpleDateFormat.setTimeZone(GMT);
				try {
					return simpleDateFormat.parse(headerValue).getTime();
				}
				catch (ParseException ex) {
					// ignore
				}
			}
		}
		return -1;
	}

	@Override
	public String getDescription(boolean includeClientInfo) {
		HttpServletRequest request = getRequest();
		StringBuilder sb = new StringBuilder();
		sb.append("uri=").append(request.getRequestURI());
		if (includeClientInfo) {
			String client = request.getRemoteAddr();
			if (StringUtils.hasLength(client)) {
				sb.append(";client=").append(client);
			}
			HttpSession session = request.getSession(false);
			if (session != null) {
				sb.append(";session=").append(session.getId());
			}
			String user = request.getRemoteUser();
			if (StringUtils.hasLength(user)) {
				sb.append(";user=").append(user);
			}
		}
		return sb.toString();
	}


	@Override
	public String toString() {
		return "ServletWebRequest: " + getDescription(true);
	}

}
