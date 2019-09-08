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

package org.springframework.web.servlet.config;

import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.w3c.dom.Element;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.feed.AtomFeedHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.xml.DomUtils;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.ContentNegotiationManagerFactoryBean;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.method.support.CompositeUriComponentsContributor;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;
import org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.JsonViewRequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.JsonViewResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ServletWebArgumentResolverAdapter;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

/**
 * A {@link BeanDefinitionParser} that provides the configuration for the
 * {@code <annotation-driven/>} MVC namespace element.
 *
 * <p>This class registers the following {@link HandlerMapping}s:</p>
 * <ul>
 * <li>{@link RequestMappingHandlerMapping}
 * ordered at 0 for mapping requests to annotated controller methods.
 * <li>{@link BeanNameUrlHandlerMapping}
 * ordered at 2 to map URL paths to controller bean names.
 * </ul>
 *
 * <p><strong>Note:</strong> Additional HandlerMappings may be registered
 * as a result of using the {@code <view-controller>} or the
 * {@code <resources>} MVC namespace elements.
 *
 * <p>This class registers the following {@link HandlerAdapter}s:
 * <ul>
 * <li>{@link RequestMappingHandlerAdapter}
 * for processing requests with annotated controller methods.
 * <li>{@link HttpRequestHandlerAdapter}
 * for processing requests with {@link HttpRequestHandler}s.
 * <li>{@link SimpleControllerHandlerAdapter}
 * for processing requests with interface-based {@link Controller}s.
 * </ul>
 *
 * <p>This class registers the following {@link HandlerExceptionResolver}s:
 * <ul>
 * <li>{@link ExceptionHandlerExceptionResolver} for handling exceptions through
 * {@link org.springframework.web.bind.annotation.ExceptionHandler} methods.
 * <li>{@link ResponseStatusExceptionResolver} for exceptions annotated
 * with {@link org.springframework.web.bind.annotation.ResponseStatus}.
 * <li>{@link DefaultHandlerExceptionResolver} for resolving known Spring
 * exception types
 * </ul>
 *
 * <p>This class registers an {@link org.springframework.util.AntPathMatcher}
 * and a {@link org.springframework.web.util.UrlPathHelper} to be used by:
 * <ul>
 * <li>the {@link RequestMappingHandlerMapping},
 * <li>the {@link HandlerMapping} for ViewControllers
 * <li>and the {@link HandlerMapping} for serving resources
 * </ul>
 * Note that those beans can be configured by using the {@code path-matching}
 * MVC namespace element.
 *
 * <p>Both the {@link RequestMappingHandlerAdapter} and the
 * {@link ExceptionHandlerExceptionResolver} are configured with instances of
 * the following by default:
 * <ul>
 * <li>A {@link ContentNegotiationManager}
 * <li>A {@link DefaultFormattingConversionService}
 * <li>A {@link org.springframework.validation.beanvalidation.LocalValidatorFactoryBean}
 * if a JSR-303 implementation is available on the classpath
 * <li>A range of {@link HttpMessageConverter}s depending on which third-party
 * libraries are available on the classpath.
 * </ul>
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Agim Emruli
 * @since 3.0
 */
class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	public static final String HANDLER_MAPPING_BEAN_NAME = RequestMappingHandlerMapping.class.getName();

	public static final String HANDLER_ADAPTER_BEAN_NAME = RequestMappingHandlerAdapter.class.getName();

	public static final String CONTENT_NEGOTIATION_MANAGER_BEAN_NAME = "mvcContentNegotiationManager";


	private static final boolean javaxValidationPresent =
			ClassUtils.isPresent("javax.validation.Validator",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static boolean romePresent =
			ClassUtils.isPresent("com.rometools.rome.feed.WireFeed",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader()) &&
			ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jackson2XmlPresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jackson2SmilePresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean jackson2CborPresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());

	private static final boolean gsonPresent =
			ClassUtils.isPresent("com.google.gson.Gson",
					AnnotationDrivenBeanDefinitionParser.class.getClassLoader());


	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		Object source = parserContext.extractSource(element);
		XmlReaderContext readerContext = parserContext.getReaderContext();

		// 这里将compDefinition push保存下来，之后当前方法发起的注册事件都会被暂存到compDefinition的nestedComponents
		// 属性，在方法最后pop后会发起注册compDefinition的事件，此时compDefinition对象包含了当前方法发起的所有注册事件
		CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
		parserContext.pushContainingComponent(compDefinition);

		// 获取xml配置的content-negotiation-manager属性并返回bean引用，如果没有则添加并返回ContentNegotiationManagerFactoryBean的bean引用
		// ContentNegotiationManagerFactoryBean返回ContentNegotiationManager实例，ContentNegotiationManager用于返回指定request对应的MediaType
		// 如application/json
		RuntimeBeanReference contentNegotiationManager = getContentNegotiationManager(element, source, parserContext);

		// 创建RequestMappingHandlerMapping bean
		RootBeanDefinition handlerMappingDef = new RootBeanDefinition(RequestMappingHandlerMapping.class);
		handlerMappingDef.setSource(source);
		handlerMappingDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		handlerMappingDef.getPropertyValues().add("order", 0);
		handlerMappingDef.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);

		if (element.hasAttribute("enable-matrix-variables")) {
			Boolean enableMatrixVariables = Boolean.valueOf(element.getAttribute("enable-matrix-variables"));
			handlerMappingDef.getPropertyValues().add("removeSemicolonContent", !enableMatrixVariables);
		}

		/*
		解析annotation-driven元素下的path-matching属性，并将相关配置添加到handlerMappingDef，如

		<mvc:annotation-driven>
        <mvc:path-matching
            suffix-pattern="true"
            trailing-slash="false"
            registered-suffixes-only="true"
            path-helper="pathHelper"
            path-matcher="pathMatcher"/>
    	</mvc:annotation-driven>

   	 	<bean id="pathHelper" class="org.example.app.MyPathHelper"/>
    	<bean id="pathMatcher" class="org.example.app.MyPathMatcher"/>
		 */
		configurePathMatchingProperties(handlerMappingDef, element, parserContext);
		// 将handlerMappingDef添加到容器
		readerContext.getRegistry().registerBeanDefinition(HANDLER_MAPPING_BEAN_NAME , handlerMappingDef);

		// 创建用于保存cors配置的bean，bean类型实际上是个LinkedHashMap，cors的配置名为key，值为value
		RuntimeBeanReference corsRef = MvcNamespaceUtils.registerCorsConfigurations(null, parserContext, source);
		// 保存cors配置bean到handlerMappingDef
		handlerMappingDef.getPropertyValues().add("corsConfigurations", corsRef);

		/*
		以上是配置RequestMappingHandlerMapping
		 */

		// 如果xml配置了conversion-service则使用指定的bean返回，否则默认为FormattingConversionServiceFactoryBean
		RuntimeBeanReference conversionService = getConversionService(element, source, parserContext);
		// 同上，默认为OptionalValidatorFactoryBean
		RuntimeBeanReference validator = getValidator(element, source, parserContext);
		// 同上，默认为null
		RuntimeBeanReference messageCodesResolver = getMessageCodesResolver(element);

		// ConfigurableWebBindingInitializer用于维护上面配置的3个bean，并能够初始化WebDataBinder对象的若干属性
		RootBeanDefinition bindingDef = new RootBeanDefinition(ConfigurableWebBindingInitializer.class);
		bindingDef.setSource(source);
		bindingDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		bindingDef.getPropertyValues().add("conversionService", conversionService);
		bindingDef.getPropertyValues().add("validator", validator);
		bindingDef.getPropertyValues().add("messageCodesResolver", messageCodesResolver);

		/*
		以上是配置ConfigurableWebBindingInitializer
		 */

		// 如果xml配置了message-converters属性则使用相应的配置，否则默认添加若干个Converter，如
		// ByteArrayHttpMessageConverter、StringHttpMessageConverter、GsonHttpMessageConverter等
		// 用于从http input stream中获取数据并转换成指定类型，或将执行类型对象输入到http output stream
		ManagedList<?> messageConverters = getMessageConverters(element, source, parserContext);
		// 如果xml配置了argument-resolvers则返回对应配置的bean，否则为null，顾名思义，用于解析方法参数的
		ManagedList<?> argumentResolvers = getArgumentResolvers(element, parserContext);
		// 同上，检查xml中的return-value-handlers配置，如果没有则为null，用于处理方法返回值
		ManagedList<?> returnValueHandlers = getReturnValueHandlers(element, parserContext);
		// 如果配置了async-support则返回，否则如果配置了default-timeout则返回，否则返回null
		String asyncTimeout = getAsyncTimeout(element);
		// 当开启async-support配置时，如果配置了task-executor则返回对应的bean引用
		RuntimeBeanReference asyncExecutor = getAsyncExecutor(element);
		// 当开启async-support配置时，如果配置了callable-interceptors则返回对应的bean引用
		ManagedList<?> callableInterceptors = getCallableInterceptors(element, source, parserContext);
		// 当开启async-support配置时，如果配置了deferred-result-interceptors则返回对应的bean引用
		ManagedList<?> deferredResultInterceptors = getDeferredResultInterceptors(element, source, parserContext);

		// 创建RequestMappingHandlerAdapter bean
		RootBeanDefinition handlerAdapterDef = new RootBeanDefinition(RequestMappingHandlerAdapter.class);
		handlerAdapterDef.setSource(source);
		handlerAdapterDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		handlerAdapterDef.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);
		handlerAdapterDef.getPropertyValues().add("webBindingInitializer", bindingDef);
		handlerAdapterDef.getPropertyValues().add("messageConverters", messageConverters);
		// 如果存在jackson相关类则添加JsonViewRequestBodyAdvice到handlerAdapterDef的requestBodyAdvice属性
		addRequestBodyAdvice(handlerAdapterDef);
		// 如果存在jackson相关类则添加JsonViewResponseBodyAdvice到handlerAdapterDef的responseBodyAdvice属性
		addResponseBodyAdvice(handlerAdapterDef);

		// 配置handlerAdapterDef的若干属性
		if (element.hasAttribute("ignore-default-model-on-redirect")) {
			Boolean ignoreDefaultModel = Boolean.valueOf(element.getAttribute("ignore-default-model-on-redirect"));
			handlerAdapterDef.getPropertyValues().add("ignoreDefaultModelOnRedirect", ignoreDefaultModel);
		}
		if (argumentResolvers != null) {
			handlerAdapterDef.getPropertyValues().add("customArgumentResolvers", argumentResolvers);
		}
		if (returnValueHandlers != null) {
			handlerAdapterDef.getPropertyValues().add("customReturnValueHandlers", returnValueHandlers);
		}
		if (asyncTimeout != null) {
			handlerAdapterDef.getPropertyValues().add("asyncRequestTimeout", asyncTimeout);
		}
		if (asyncExecutor != null) {
			handlerAdapterDef.getPropertyValues().add("taskExecutor", asyncExecutor);
		}

		handlerAdapterDef.getPropertyValues().add("callableInterceptors", callableInterceptors);
		handlerAdapterDef.getPropertyValues().add("deferredResultInterceptors", deferredResultInterceptors);
		readerContext.getRegistry().registerBeanDefinition(HANDLER_ADAPTER_BEAN_NAME , handlerAdapterDef);

		/*
		以上是配置RequestMappingHandlerAdapter
		 */

		// 创建CompositeUriComponentsContributorFactoryBean bean
		RootBeanDefinition uriContributorDef =
				new RootBeanDefinition(CompositeUriComponentsContributorFactoryBean.class);
		uriContributorDef.setSource(source);
		uriContributorDef.getPropertyValues().addPropertyValue("handlerAdapter", handlerAdapterDef);
		uriContributorDef.getPropertyValues().addPropertyValue("conversionService", conversionService);
		String uriContributorName = MvcUriComponentsBuilder.MVC_URI_COMPONENTS_CONTRIBUTOR_BEAN_NAME;
		readerContext.getRegistry().registerBeanDefinition(uriContributorName, uriContributorDef);

		// 添加ConversionServiceExposingInterceptor和MappedInterceptor bean，ConversionServiceExposingInterceptor类继承自HandlerInterceptorAdapter
		// 在处理请求之前将conversionService添加到请求的属性中，MappedInterceptor的作用是在HandlerInterceptor接口的基础上添加了matches方法，能够根据配置判断
		// 是否需要将其维护的HandlerInterceptor添加到HandlerExecutionChain中，MappedInterceptor的使用可以看AbstractHandlerMapping类的getHandlerExecutionChain方法
		RootBeanDefinition csInterceptorDef = new RootBeanDefinition(ConversionServiceExposingInterceptor.class);
		csInterceptorDef.setSource(source);
		csInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(0, conversionService);
		RootBeanDefinition mappedInterceptorDef = new RootBeanDefinition(MappedInterceptor.class);
		mappedInterceptorDef.setSource(source);
		mappedInterceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(0, (Object) null);
		// 将ConversionServiceExposingInterceptor添加到创建出来的MappedInterceptor中
		mappedInterceptorDef.getConstructorArgumentValues().addIndexedArgumentValue(1, csInterceptorDef);
		// 将mappedInterceptorDef添加到beanFactory，beanName自动生成
		String mappedInterceptorName = readerContext.registerWithGeneratedName(mappedInterceptorDef);

		// 添加ExceptionHandlerExceptionResolver bean，ExceptionHandlerExceptionResolver用于处理执行请求时发生的异常
		RootBeanDefinition methodExceptionResolver = new RootBeanDefinition(ExceptionHandlerExceptionResolver.class);
		methodExceptionResolver.setSource(source);
		methodExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		methodExceptionResolver.getPropertyValues().add("contentNegotiationManager", contentNegotiationManager);
		methodExceptionResolver.getPropertyValues().add("messageConverters", messageConverters);
		// 该异常处理优先级最高
		methodExceptionResolver.getPropertyValues().add("order", 0);
		addResponseBodyAdvice(methodExceptionResolver);
		if (argumentResolvers != null) {
			methodExceptionResolver.getPropertyValues().add("customArgumentResolvers", argumentResolvers);
		}
		if (returnValueHandlers != null) {
			methodExceptionResolver.getPropertyValues().add("customReturnValueHandlers", returnValueHandlers);
		}
		String methodExResolverName = readerContext.registerWithGeneratedName(methodExceptionResolver);

		// 添加ResponseStatusExceptionResolver bean，ResponseStatusExceptionResolver类对ResponseStatus注解提供了支持，ResponseStatus的作用
		// 是设置发生指定异常时的状态码和异常原因
		RootBeanDefinition statusExceptionResolver = new RootBeanDefinition(ResponseStatusExceptionResolver.class);
		statusExceptionResolver.setSource(source);
		statusExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		// 该异常处理优先级小于ExceptionHandlerExceptionResolver
		statusExceptionResolver.getPropertyValues().add("order", 1);
		String statusExResolverName = readerContext.registerWithGeneratedName(statusExceptionResolver);

		// 添加DefaultHandlerExceptionResolver bean，DefaultHandlerExceptionResolver类支持常见的异常的处理
		RootBeanDefinition defaultExceptionResolver = new RootBeanDefinition(DefaultHandlerExceptionResolver.class);
		defaultExceptionResolver.setSource(source);
		defaultExceptionResolver.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		// 该异常处理优先级小于ResponseStatusExceptionResolver
		defaultExceptionResolver.getPropertyValues().add("order", 2);
		String defaultExResolverName = readerContext.registerWithGeneratedName(defaultExceptionResolver);

		// 发起bean的注册事件
		parserContext.registerComponent(new BeanComponentDefinition(handlerMappingDef, HANDLER_MAPPING_BEAN_NAME));
		parserContext.registerComponent(new BeanComponentDefinition(handlerAdapterDef, HANDLER_ADAPTER_BEAN_NAME));
		parserContext.registerComponent(new BeanComponentDefinition(uriContributorDef, uriContributorName));
		parserContext.registerComponent(new BeanComponentDefinition(mappedInterceptorDef, mappedInterceptorName));
		parserContext.registerComponent(new BeanComponentDefinition(methodExceptionResolver, methodExResolverName));
		parserContext.registerComponent(new BeanComponentDefinition(statusExceptionResolver, statusExResolverName));
		parserContext.registerComponent(new BeanComponentDefinition(defaultExceptionResolver, defaultExResolverName));

		// Ensure BeanNameUrlHandlerMapping (SPR-8289) and default HandlerAdapters are not "turned off"
		// 确保BeanNameUrlHandlerMapping、HttpRequestHandlerAdapter、SimpleControllerHandlerAdapter和HandlerMappingIntrospector
		// 这4个bean已注册，不存在则注册
		MvcNamespaceUtils.registerDefaultComponents(parserContext, source);

		parserContext.popAndRegisterContainingComponent();

		return null;
	}

	protected void addRequestBodyAdvice(RootBeanDefinition beanDef) {
		if (jackson2Present) {
			beanDef.getPropertyValues().add("requestBodyAdvice",
					new RootBeanDefinition(JsonViewRequestBodyAdvice.class));
		}
	}

	protected void addResponseBodyAdvice(RootBeanDefinition beanDef) {
		if (jackson2Present) {
			beanDef.getPropertyValues().add("responseBodyAdvice",
					new RootBeanDefinition(JsonViewResponseBodyAdvice.class));
		}
	}

	private RuntimeBeanReference getConversionService(
			Element element, @Nullable Object source, ParserContext parserContext) {

		RuntimeBeanReference conversionServiceRef;
		if (element.hasAttribute("conversion-service")) {
			conversionServiceRef = new RuntimeBeanReference(element.getAttribute("conversion-service"));
		}
		else {
			RootBeanDefinition conversionDef = new RootBeanDefinition(FormattingConversionServiceFactoryBean.class);
			conversionDef.setSource(source);
			conversionDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String conversionName = parserContext.getReaderContext().registerWithGeneratedName(conversionDef);
			parserContext.registerComponent(new BeanComponentDefinition(conversionDef, conversionName));
			conversionServiceRef = new RuntimeBeanReference(conversionName);
		}
		return conversionServiceRef;
	}

	@Nullable
	private RuntimeBeanReference getValidator(Element element, @Nullable Object source, ParserContext parserContext) {
		if (element.hasAttribute("validator")) {
			return new RuntimeBeanReference(element.getAttribute("validator"));
		}
		else if (javaxValidationPresent) {
			RootBeanDefinition validatorDef = new RootBeanDefinition(
					"org.springframework.validation.beanvalidation.OptionalValidatorFactoryBean");
			validatorDef.setSource(source);
			validatorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			String validatorName = parserContext.getReaderContext().registerWithGeneratedName(validatorDef);
			parserContext.registerComponent(new BeanComponentDefinition(validatorDef, validatorName));
			return new RuntimeBeanReference(validatorName);
		}
		else {
			return null;
		}
	}

	private RuntimeBeanReference getContentNegotiationManager(
			Element element, @Nullable Object source, ParserContext parserContext) {

		RuntimeBeanReference beanRef;
		if (element.hasAttribute("content-negotiation-manager")) {
			String name = element.getAttribute("content-negotiation-manager");
			beanRef = new RuntimeBeanReference(name);
		}
		else {
			RootBeanDefinition factoryBeanDef = new RootBeanDefinition(ContentNegotiationManagerFactoryBean.class);
			factoryBeanDef.setSource(source);
			factoryBeanDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
			factoryBeanDef.getPropertyValues().add("mediaTypes", getDefaultMediaTypes());
			String name = CONTENT_NEGOTIATION_MANAGER_BEAN_NAME;
			parserContext.getReaderContext().getRegistry().registerBeanDefinition(name , factoryBeanDef);
			parserContext.registerComponent(new BeanComponentDefinition(factoryBeanDef, name));
			beanRef = new RuntimeBeanReference(name);
		}
		return beanRef;
	}

	private void configurePathMatchingProperties(
			RootBeanDefinition handlerMappingDef, Element element, ParserContext parserContext) {

		Element pathMatchingElement = DomUtils.getChildElementByTagName(element, "path-matching");
		if (pathMatchingElement != null) {
			Object source = parserContext.extractSource(element);

			if (pathMatchingElement.hasAttribute("suffix-pattern")) {
				Boolean useSuffixPatternMatch = Boolean.valueOf(pathMatchingElement.getAttribute("suffix-pattern"));
				handlerMappingDef.getPropertyValues().add("useSuffixPatternMatch", useSuffixPatternMatch);
			}
			if (pathMatchingElement.hasAttribute("trailing-slash")) {
				Boolean useTrailingSlashMatch = Boolean.valueOf(pathMatchingElement.getAttribute("trailing-slash"));
				handlerMappingDef.getPropertyValues().add("useTrailingSlashMatch", useTrailingSlashMatch);
			}
			if (pathMatchingElement.hasAttribute("registered-suffixes-only")) {
				Boolean useRegisteredSuffixPatternMatch = Boolean.valueOf(pathMatchingElement.getAttribute("registered-suffixes-only"));
				handlerMappingDef.getPropertyValues().add("useRegisteredSuffixPatternMatch", useRegisteredSuffixPatternMatch);
			}

			RuntimeBeanReference pathHelperRef = null;
			if (pathMatchingElement.hasAttribute("path-helper")) {
				pathHelperRef = new RuntimeBeanReference(pathMatchingElement.getAttribute("path-helper"));
			}
			pathHelperRef = MvcNamespaceUtils.registerUrlPathHelper(pathHelperRef, parserContext, source);
			handlerMappingDef.getPropertyValues().add("urlPathHelper", pathHelperRef);

			RuntimeBeanReference pathMatcherRef = null;
			if (pathMatchingElement.hasAttribute("path-matcher")) {
				pathMatcherRef = new RuntimeBeanReference(pathMatchingElement.getAttribute("path-matcher"));
			}
			pathMatcherRef = MvcNamespaceUtils.registerPathMatcher(pathMatcherRef, parserContext, source);
			handlerMappingDef.getPropertyValues().add("pathMatcher", pathMatcherRef);
		}
	}

	private Properties getDefaultMediaTypes() {
		Properties defaultMediaTypes = new Properties();
		if (romePresent) {
			defaultMediaTypes.put("atom", MediaType.APPLICATION_ATOM_XML_VALUE);
			defaultMediaTypes.put("rss", MediaType.APPLICATION_RSS_XML_VALUE);
		}
		if (jaxb2Present || jackson2XmlPresent) {
			defaultMediaTypes.put("xml", MediaType.APPLICATION_XML_VALUE);
		}
		if (jackson2Present || gsonPresent) {
			defaultMediaTypes.put("json", MediaType.APPLICATION_JSON_VALUE);
		}
		if (jackson2SmilePresent) {
			defaultMediaTypes.put("smile", "application/x-jackson-smile");
		}
		if (jackson2CborPresent) {
			defaultMediaTypes.put("cbor", "application/cbor");
		}
		return defaultMediaTypes;
	}

	@Nullable
	private RuntimeBeanReference getMessageCodesResolver(Element element) {
		if (element.hasAttribute("message-codes-resolver")) {
			return new RuntimeBeanReference(element.getAttribute("message-codes-resolver"));
		}
		else {
			return null;
		}
	}

	@Nullable
	private String getAsyncTimeout(Element element) {
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		return (asyncElement != null ? asyncElement.getAttribute("default-timeout") : null);
	}

	@Nullable
	private RuntimeBeanReference getAsyncExecutor(Element element) {
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		if (asyncElement != null && asyncElement.hasAttribute("task-executor")) {
			return new RuntimeBeanReference(asyncElement.getAttribute("task-executor"));
		}
		return null;
	}

	private ManagedList<?> getCallableInterceptors(
			Element element, @Nullable Object source, ParserContext parserContext) {

		ManagedList<? super Object> interceptors = new ManagedList<>();
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		if (asyncElement != null) {
			Element interceptorsElement = DomUtils.getChildElementByTagName(asyncElement, "callable-interceptors");
			if (interceptorsElement != null) {
				interceptors.setSource(source);
				for (Element converter : DomUtils.getChildElementsByTagName(interceptorsElement, "bean")) {
					BeanDefinitionHolder beanDef = parserContext.getDelegate().parseBeanDefinitionElement(converter);
					if (beanDef != null) {
						beanDef = parserContext.getDelegate().decorateBeanDefinitionIfRequired(converter, beanDef);
						interceptors.add(beanDef);
					}
				}
			}
		}
		return interceptors;
	}

	private ManagedList<?> getDeferredResultInterceptors(
			Element element, @Nullable Object source, ParserContext parserContext) {

		ManagedList<? super Object> interceptors = new ManagedList<>();
		Element asyncElement = DomUtils.getChildElementByTagName(element, "async-support");
		if (asyncElement != null) {
			Element interceptorsElement = DomUtils.getChildElementByTagName(asyncElement, "deferred-result-interceptors");
			if (interceptorsElement != null) {
				interceptors.setSource(source);
				for (Element converter : DomUtils.getChildElementsByTagName(interceptorsElement, "bean")) {
					BeanDefinitionHolder beanDef = parserContext.getDelegate().parseBeanDefinitionElement(converter);
					if (beanDef != null) {
						beanDef = parserContext.getDelegate().decorateBeanDefinitionIfRequired(converter, beanDef);
						interceptors.add(beanDef);
					}
				}
			}
		}
		return interceptors;
	}

	@Nullable
	private ManagedList<?> getArgumentResolvers(Element element, ParserContext parserContext) {
		Element resolversElement = DomUtils.getChildElementByTagName(element, "argument-resolvers");
		if (resolversElement != null) {
			ManagedList<Object> resolvers = extractBeanSubElements(resolversElement, parserContext);
			return wrapLegacyResolvers(resolvers, parserContext);
		}
		return null;
	}

	private ManagedList<Object> wrapLegacyResolvers(List<Object> list, ParserContext context) {
		ManagedList<Object> result = new ManagedList<>();
		for (Object object : list) {
			if (object instanceof BeanDefinitionHolder) {
				BeanDefinitionHolder beanDef = (BeanDefinitionHolder) object;
				String className = beanDef.getBeanDefinition().getBeanClassName();
				Assert.notNull(className, "No resolver class");
				Class<?> clazz = ClassUtils.resolveClassName(className, context.getReaderContext().getBeanClassLoader());
				if (WebArgumentResolver.class.isAssignableFrom(clazz)) {
					RootBeanDefinition adapter = new RootBeanDefinition(ServletWebArgumentResolverAdapter.class);
					adapter.getConstructorArgumentValues().addIndexedArgumentValue(0, beanDef);
					result.add(new BeanDefinitionHolder(adapter, beanDef.getBeanName() + "Adapter"));
					continue;
				}
			}
			result.add(object);
		}
		return result;
	}

	@Nullable
	private ManagedList<?> getReturnValueHandlers(Element element, ParserContext parserContext) {
		Element handlers = DomUtils.getChildElementByTagName(element, "return-value-handlers");
		return (handlers != null ? extractBeanSubElements(handlers, parserContext) : null);
	}

	private ManagedList<?> getMessageConverters(Element element, @Nullable Object source, ParserContext parserContext) {
		Element convertersElement = DomUtils.getChildElementByTagName(element, "message-converters");
		ManagedList<? super Object> messageConverters = new ManagedList<>();
		if (convertersElement != null) {
			messageConverters.setSource(source);
			for (Element beanElement : DomUtils.getChildElementsByTagName(convertersElement, "bean", "ref")) {
				Object object = parserContext.getDelegate().parsePropertySubElement(beanElement, null);
				messageConverters.add(object);
			}
		}

		if (convertersElement == null || Boolean.valueOf(convertersElement.getAttribute("register-defaults"))) {
			messageConverters.setSource(source);
			messageConverters.add(createConverterDefinition(ByteArrayHttpMessageConverter.class, source));

			RootBeanDefinition stringConverterDef = createConverterDefinition(StringHttpMessageConverter.class, source);
			stringConverterDef.getPropertyValues().add("writeAcceptCharset", false);
			messageConverters.add(stringConverterDef);

			messageConverters.add(createConverterDefinition(ResourceHttpMessageConverter.class, source));
			messageConverters.add(createConverterDefinition(ResourceRegionHttpMessageConverter.class, source));
			messageConverters.add(createConverterDefinition(SourceHttpMessageConverter.class, source));
			messageConverters.add(createConverterDefinition(AllEncompassingFormHttpMessageConverter.class, source));

			if (romePresent) {
				messageConverters.add(createConverterDefinition(AtomFeedHttpMessageConverter.class, source));
				messageConverters.add(createConverterDefinition(RssChannelHttpMessageConverter.class, source));
			}

			if (jackson2XmlPresent) {
				Class<?> type = MappingJackson2XmlHttpMessageConverter.class;
				RootBeanDefinition jacksonConverterDef = createConverterDefinition(type, source);
				GenericBeanDefinition jacksonFactoryDef = createObjectMapperFactoryDefinition(source);
				jacksonFactoryDef.getPropertyValues().add("createXmlMapper", true);
				jacksonConverterDef.getConstructorArgumentValues().addIndexedArgumentValue(0, jacksonFactoryDef);
				messageConverters.add(jacksonConverterDef);
			}
			else if (jaxb2Present) {
				messageConverters.add(createConverterDefinition(Jaxb2RootElementHttpMessageConverter.class, source));
			}

			if (jackson2Present) {
				Class<?> type = MappingJackson2HttpMessageConverter.class;
				RootBeanDefinition jacksonConverterDef = createConverterDefinition(type, source);
				GenericBeanDefinition jacksonFactoryDef = createObjectMapperFactoryDefinition(source);
				jacksonConverterDef.getConstructorArgumentValues().addIndexedArgumentValue(0, jacksonFactoryDef);
				messageConverters.add(jacksonConverterDef);
			}
			else if (gsonPresent) {
				messageConverters.add(createConverterDefinition(GsonHttpMessageConverter.class, source));
			}

			if (jackson2SmilePresent) {
				Class<?> type = MappingJackson2SmileHttpMessageConverter.class;
				RootBeanDefinition jacksonConverterDef = createConverterDefinition(type, source);
				GenericBeanDefinition jacksonFactoryDef = createObjectMapperFactoryDefinition(source);
				jacksonFactoryDef.getPropertyValues().add("factory", new SmileFactory());
				jacksonConverterDef.getConstructorArgumentValues().addIndexedArgumentValue(0, jacksonFactoryDef);
				messageConverters.add(jacksonConverterDef);
			}
			if (jackson2CborPresent) {
				Class<?> type = MappingJackson2CborHttpMessageConverter.class;
				RootBeanDefinition jacksonConverterDef = createConverterDefinition(type, source);
				GenericBeanDefinition jacksonFactoryDef = createObjectMapperFactoryDefinition(source);
				jacksonFactoryDef.getPropertyValues().add("factory", new CBORFactory());
				jacksonConverterDef.getConstructorArgumentValues().addIndexedArgumentValue(0, jacksonFactoryDef);
				messageConverters.add(jacksonConverterDef);
			}
		}
		return messageConverters;
	}

	private GenericBeanDefinition createObjectMapperFactoryDefinition(@Nullable Object source) {
		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(Jackson2ObjectMapperFactoryBean.class);
		beanDefinition.setSource(source);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		return beanDefinition;
	}

	private RootBeanDefinition createConverterDefinition(Class<?> converterClass, @Nullable Object source) {
		RootBeanDefinition beanDefinition = new RootBeanDefinition(converterClass);
		beanDefinition.setSource(source);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		return beanDefinition;
	}

	private ManagedList<Object> extractBeanSubElements(Element parentElement, ParserContext parserContext) {
		ManagedList<Object> list = new ManagedList<>();
		list.setSource(parserContext.extractSource(parentElement));
		for (Element beanElement : DomUtils.getChildElementsByTagName(parentElement, "bean", "ref")) {
			Object object = parserContext.getDelegate().parsePropertySubElement(beanElement, null);
			list.add(object);
		}
		return list;
	}


	/**
	 * A FactoryBean for a CompositeUriComponentsContributor that obtains the
	 * HandlerMethodArgumentResolver's configured in RequestMappingHandlerAdapter
	 * after it is fully initialized.
	 */
	static class CompositeUriComponentsContributorFactoryBean
			implements FactoryBean<CompositeUriComponentsContributor>, InitializingBean {

		@Nullable
		private RequestMappingHandlerAdapter handlerAdapter;

		@Nullable
		private ConversionService conversionService;

		@Nullable
		private CompositeUriComponentsContributor uriComponentsContributor;

		public void setHandlerAdapter(RequestMappingHandlerAdapter handlerAdapter) {
			this.handlerAdapter = handlerAdapter;
		}

		public void setConversionService(ConversionService conversionService) {
			this.conversionService = conversionService;
		}

		@Override
		public void afterPropertiesSet() {
			Assert.state(this.handlerAdapter != null, "No RequestMappingHandlerAdapter set");
			this.uriComponentsContributor = new CompositeUriComponentsContributor(
					this.handlerAdapter.getArgumentResolvers(), this.conversionService);
		}

		@Override
		@Nullable
		public CompositeUriComponentsContributor getObject() {
			return this.uriComponentsContributor;
		}

		@Override
		public Class<?> getObjectType() {
			return CompositeUriComponentsContributor.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}

}
