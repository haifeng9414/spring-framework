/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.mvc.condition.NameValueExpression;
import org.springframework.web.util.WebUtils;

/**
 * Abstract base class for classes for which {@link RequestMappingInfo} defines
 * the mapping between a request and a handler method.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public abstract class RequestMappingInfoHandlerMapping extends AbstractHandlerMethodMapping<RequestMappingInfo> {

	private static final Method HTTP_OPTIONS_HANDLE_METHOD;

	static {
		try {
			HTTP_OPTIONS_HANDLE_METHOD = HttpOptionsHandler.class.getMethod("handle");
		}
		catch (NoSuchMethodException ex) {
			// Should never happen
			throw new IllegalStateException("Failed to retrieve internal handler method for HTTP OPTIONS", ex);
		}
	}


	protected RequestMappingInfoHandlerMapping() {
		setHandlerMethodMappingNamingStrategy(new RequestMappingInfoHandlerMethodMappingNamingStrategy());
	}


	/**
	 * Get the URL path patterns associated with this {@link RequestMappingInfo}.
	 */
	@Override
	protected Set<String> getMappingPathPatterns(RequestMappingInfo info) {
		return info.getPatternsCondition().getPatterns();
	}

	/**
	 * Check if the given RequestMappingInfo matches the current request and
	 * return a (potentially new) instance with conditions that match the
	 * current request -- for example with a subset of URL patterns.
	 * @return an info in case of a match; or {@code null} otherwise.
	 */
	@Override
	protected RequestMappingInfo getMatchingMapping(RequestMappingInfo info, HttpServletRequest request) {
		return info.getMatchingCondition(request);
	}

	/**
	 * Provide a Comparator to sort RequestMappingInfos matched to a request.
	 */
	@Override
	protected Comparator<RequestMappingInfo> getMappingComparator(final HttpServletRequest request) {
		return (info1, info2) -> info1.compareTo(info2, request);
	}

	/**
	 * Expose URI template variables, matrix variables, and producible media types in the request.
	 * @see HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE
	 * @see HandlerMapping#MATRIX_VARIABLES_ATTRIBUTE
	 * @see HandlerMapping#PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE
	 */
	@Override
	// 在找到匹配到的mapping时执行
	protected void handleMatch(RequestMappingInfo info, String lookupPath, HttpServletRequest request) {
		super.handleMatch(info, lookupPath, request);

		String bestPattern;
		Map<String, String> uriVariables;
		Map<String, String> decodedUriVariables;

		Set<String> patterns = info.getPatternsCondition().getPatterns();
		// 如果mapping配置的路径为空
		if (patterns.isEmpty()) {
			bestPattern = lookupPath;
			uriVariables = Collections.emptyMap();
			decodedUriVariables = Collections.emptyMap();
		}
		else {
			bestPattern = patterns.iterator().next();
			// 按照ant风格解析mapping路径，获取路径中的变量，如这里的为参数/{test}/addUser和/demo/addUser，则返回test -> demo
			uriVariables = getPathMatcher().extractUriTemplateVariables(bestPattern, lookupPath);
			// 对参数进行url解码，默认不进行解码
			decodedUriVariables = getUrlPathHelper().decodePathVariables(request, uriVariables);
		}

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestPattern);
		request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, decodedUriVariables);

		// 是否移除请求路径中的分号，默认为true，对于为true的情况下，可以设置enable-matrix-variables为true以开启MatrixVariable的支持，如
		// <mvc:annotation-driven enable-matrix-variables="true"/>
		if (isMatrixVariableContentAvailable()) {
			// extractMatrixVariables方法解析请求路径中的matrixVariables，保存每个请求路径后面的matrixVariables到map，并将请求路径
			// 参数名作为key，map作为value返回
			Map<String, MultiValueMap<String, String>> matrixVars = extractMatrixVariables(request, uriVariables);
			request.setAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, matrixVars);
		}

		if (!info.getProducesCondition().getProducibleMediaTypes().isEmpty()) {
			// 如果RequestMapping注解设置了produces属性限制请求的MediaType，则在这里保存到request中
			Set<MediaType> mediaTypes = info.getProducesCondition().getProducibleMediaTypes();
			request.setAttribute(PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, mediaTypes);
		}
	}

	private boolean isMatrixVariableContentAvailable() {
		return !getUrlPathHelper().shouldRemoveSemicolonContent();
	}

	private Map<String, MultiValueMap<String, String>> extractMatrixVariables(
			HttpServletRequest request, Map<String, String> uriVariables) {

		Map<String, MultiValueMap<String, String>> result = new LinkedHashMap<>();
		uriVariables.forEach((uriVarKey, uriVarValue) -> {
			int equalsIndex = uriVarValue.indexOf('=');
			if (equalsIndex == -1) {
				return;
			}

			String matrixVariables;

			int semicolonIndex = uriVarValue.indexOf(';');
			if ((semicolonIndex == -1) || (semicolonIndex == 0) || (equalsIndex < semicolonIndex)) {
				matrixVariables = uriVarValue;
			}
			else {
				// 保存请求路径参数后面的matrixVariables
				matrixVariables = uriVarValue.substring(semicolonIndex + 1);
				// 截掉请求路径参数后面的matrixVariables
				uriVariables.put(uriVarKey, uriVarValue.substring(0, semicolonIndex));
			}

			MultiValueMap<String, String> vars = WebUtils.parseMatrixVariables(matrixVariables);
			result.put(uriVarKey, getUrlPathHelper().decodeMatrixVariables(request, vars));
		});
		return result;
	}

	/**
	 * Iterate all RequestMappingInfo's once again, look if any match by URL at
	 * least and raise exceptions according to what doesn't match.
	 * @throws HttpRequestMethodNotSupportedException if there are matches by URL
	 * but not by HTTP method
	 * @throws HttpMediaTypeNotAcceptableException if there are matches by URL
	 * but not by consumable/producible media types
	 */
	@Override
	// handleNoMatch方法在没有找到合适的mapping时被执行，执行的主要目的是告诉客户端，为什么没有找到匹配的mapping
	protected HandlerMethod handleNoMatch(
			Set<RequestMappingInfo> infos, String lookupPath, HttpServletRequest request) throws ServletException {

		/*
		 PartialMatchHelper对象在构造函数中遍历所有的RequestMappingInfo，如果RequestMappingInfo对应的路径能够匹配当前的请求路径
		 则添加到PartialMatchHelper对象的partialMatches中，在判断是否匹配的过程中，会将RequestMappingInfo对应的路径，既pattern
		 分别用4中判断是否匹配：
		 1.直接匹配，既pattern是否等于请求路径
		 2.在pattern后加上.*，需要useSuffixPatternMatch属性为true
		 3.按照ant风格解析进行匹配 既将pattern理解为ant表达式
		 4.在pattern后加上/进行匹配 需要useTrailingSlashMatch属性为true

		 每个RequestMappingInfo的所有pattern都会用上面4尝试匹配（如果useSuffixPatternMatch和useTrailingSlashMatch都为true），如果某种情况成功
		 匹配上了，则跟其RequestMappingInfo一块保存到PartialMatchHelper对象的partialMatches属性中
		 */

		PartialMatchHelper helper = new PartialMatchHelper(infos, request);
		if (helper.isEmpty()) {
			return null;
		}

		// 如果partialMatches中包含的RequestMappingInfo中没有和当前请求的method相对应的
		if (helper.hasMethodsMismatch()) {
			// 取所有RequestMappingInfo的method属性的并集
			Set<String> methods = helper.getAllowedMethods();
			// 如果当前请求是option请求，则直接返回HttpOptionsHandler，并将从RequestMappingInfo中找到的method属性的并集保存到HttpOptionsHandler
			// 中用于响应
			if (HttpMethod.OPTIONS.matches(request.getMethod())) {
				HttpOptionsHandler handler = new HttpOptionsHandler(methods);
				return new HandlerMethod(handler, HTTP_OPTIONS_HANDLE_METHOD);
			}
			// 如果当前请求是不option的，而partialMatches中包含的RequestMappingInfo中又没有method与当前请求的method相匹配，则抛出异常
			throw new HttpRequestMethodNotSupportedException(request.getMethod(), methods);
		}

		/*
		HTTP协议Header中有两个东西：ContentType和Accept，在请求中，ContentType用来告诉服务器当前发送的数据是什么格式，
		Accept用来告诉服务器，客户端能认识哪些格式，而在RequestMapping注解中可以对请求的这两个参数进行限制，consumes用来限制ContentType，produces用来限制Accept
		 */

		// 如果partialMatches中包含的RequestMappingInfo中存在method和consumes指定的ContentType与当前请求的method和ContentType相匹配，则这里返回false
		// 如果这里返回true，说明没有RequestMappingInfo的consumes与当前请求的ContentType相匹配，也就是不支持当前请求的ContentType中定义的格式
		if (helper.hasConsumesMismatch()) {
			// 获取method与当前请求的method相匹配的RequestMappingInfo配置的consumes属性值，也就是其支持的ContentType对应的MediaType列表
			Set<MediaType> mediaTypes = helper.getConsumableMediaTypes();
			MediaType contentType = null;
			if (StringUtils.hasLength(request.getContentType())) {
				try {
					contentType = MediaType.parseMediaType(request.getContentType());
				}
				catch (InvalidMediaTypeException ex) {
					throw new HttpMediaTypeNotSupportedException(ex.getMessage());
				}
			}
			// 将所有找到的MediaType作为异常抛出，提示客户端应该使用什么MediaType作为ContentType
			throw new HttpMediaTypeNotSupportedException(contentType, new ArrayList<>(mediaTypes));
		}

		// 同上，这里针对的是Accept
		if (helper.hasProducesMismatch()) {
			Set<MediaType> mediaTypes = helper.getProducibleMediaTypes();
			throw new HttpMediaTypeNotAcceptableException(new ArrayList<>(mediaTypes));
		}

		// 最后是判断RequestMappingInfo中是否存在param与当前请求的请求路径参数是否匹配，如有如下RequestMapping注解配置：
		// @GetMapping(value = "/show-value", params = {"a=1", "c=3"})，对于请求show-value?a=1&b=2将会被拒绝，show-value?a=1&c=3才可以
		if (helper.hasParamsMismatch()) {
			List<String[]> conditions = helper.getParamConditions();
			throw new UnsatisfiedServletRequestParameterException(conditions, request.getParameterMap());
		}

		return null;
	}


	/**
	 * Aggregate all partial matches and expose methods checking across them.
	 */
	private static class PartialMatchHelper {

		private final List<PartialMatch> partialMatches = new ArrayList<>();

		public PartialMatchHelper(Set<RequestMappingInfo> infos, HttpServletRequest request) {
			for (RequestMappingInfo info : infos) {
				if (info.getPatternsCondition().getMatchingCondition(request) != null) {
					this.partialMatches.add(new PartialMatch(info, request));
				}
			}
		}

		/**
		 * Whether there any partial matches.
		 */
		public boolean isEmpty() {
			return this.partialMatches.isEmpty();
		}

		/**
		 * Any partial matches for "methods"?
		 */
		public boolean hasMethodsMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasMethodsMatch()) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Any partial matches for "methods" and "consumes"?
		 */
		public boolean hasConsumesMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasConsumesMatch()) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Any partial matches for "methods", "consumes", and "produces"?
		 */
		public boolean hasProducesMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasProducesMatch()) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Any partial matches for "methods", "consumes", "produces", and "params"?
		 */
		public boolean hasParamsMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasParamsMatch()) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Return declared HTTP methods.
		 */
		public Set<String> getAllowedMethods() {
			Set<String> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				for (RequestMethod method : match.getInfo().getMethodsCondition().getMethods()) {
					result.add(method.name());
				}
			}
			return result;
		}

		/**
		 * Return declared "consumable" types but only among those that also
		 * match the "methods" condition.
		 */
		public Set<MediaType> getConsumableMediaTypes() {
			Set<MediaType> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasMethodsMatch()) {
					result.addAll(match.getInfo().getConsumesCondition().getConsumableMediaTypes());
				}
			}
			return result;
		}

		/**
		 * Return declared "producible" types but only among those that also
		 * match the "methods" and "consumes" conditions.
		 */
		public Set<MediaType> getProducibleMediaTypes() {
			Set<MediaType> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasConsumesMatch()) {
					result.addAll(match.getInfo().getProducesCondition().getProducibleMediaTypes());
				}
			}
			return result;
		}

		/**
		 * Return declared "params" conditions but only among those that also
		 * match the "methods", "consumes", and "params" conditions.
		 */
		public List<String[]> getParamConditions() {
			List<String[]> result = new ArrayList<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasProducesMatch()) {
					Set<NameValueExpression<String>> set = match.getInfo().getParamsCondition().getExpressions();
					if (!CollectionUtils.isEmpty(set)) {
						int i = 0;
						String[] array = new String[set.size()];
						for (NameValueExpression<String> expression : set) {
							array[i++] = expression.toString();
						}
						result.add(array);
					}
				}
			}
			return result;
		}


		/**
		 * Container for a RequestMappingInfo that matches the URL path at least.
		 */
		private static class PartialMatch {

			private final RequestMappingInfo info;

			private final boolean methodsMatch;

			private final boolean consumesMatch;

			private final boolean producesMatch;

			private final boolean paramsMatch;

			/**
			 * @param info RequestMappingInfo that matches the URL path.
			 * @param request the current request
			 */
			public PartialMatch(RequestMappingInfo info, HttpServletRequest request) {
				this.info = info;
				this.methodsMatch = (info.getMethodsCondition().getMatchingCondition(request) != null);
				this.consumesMatch = (info.getConsumesCondition().getMatchingCondition(request) != null);
				this.producesMatch = (info.getProducesCondition().getMatchingCondition(request) != null);
				this.paramsMatch = (info.getParamsCondition().getMatchingCondition(request) != null);
			}

			public RequestMappingInfo getInfo() {
				return this.info;
			}

			public boolean hasMethodsMatch() {
				return this.methodsMatch;
			}

			public boolean hasConsumesMatch() {
				return (hasMethodsMatch() && this.consumesMatch);
			}

			public boolean hasProducesMatch() {
				return (hasConsumesMatch() && this.producesMatch);
			}

			public boolean hasParamsMatch() {
				return (hasProducesMatch() && this.paramsMatch);
			}

			@Override
			public String toString() {
				return this.info.toString();
			}
		}
	}


	/**
	 * Default handler for HTTP OPTIONS.
	 */
	private static class HttpOptionsHandler {

		private final HttpHeaders headers = new HttpHeaders();

		public HttpOptionsHandler(Set<String> declaredMethods) {
			this.headers.setAllow(initAllowedHttpMethods(declaredMethods));
		}

		private static Set<HttpMethod> initAllowedHttpMethods(Set<String> declaredMethods) {
			Set<HttpMethod> result = new LinkedHashSet<>(declaredMethods.size());
			if (declaredMethods.isEmpty()) {
				for (HttpMethod method : HttpMethod.values()) {
					if (method != HttpMethod.TRACE) {
						result.add(method);
					}
				}
			}
			else {
				for (String method : declaredMethods) {
					HttpMethod httpMethod = HttpMethod.valueOf(method);
					result.add(httpMethod);
					if (httpMethod == HttpMethod.GET) {
						result.add(HttpMethod.HEAD);
					}
				}
				result.add(HttpMethod.OPTIONS);
			}
			return result;
		}

		@SuppressWarnings("unused")
		public HttpHeaders handle() {
			return this.headers;
		}
	}

}
