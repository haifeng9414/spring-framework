先看源码，最后再结合例子和源码分析，首先看之后将会讲到的例子中的xml配置：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.0.xsd http://www.springframework.org/schema/mvc http://www.springframework.org/schema/mvc/spring-mvc.xsd http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="com.dhf"/>
    <mvc:annotation-driven/>

    <!--解决对象转json问题-->
    <bean class="org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter">
        <property name="messageConverters">
            <list>
                <ref bean="jsonMessageConverter"/>
            </list>
        </property>
    </bean>

    <bean id="jsonMessageConverter"
          class="org.springframework.http.converter.json.MappingJackson2HttpMessageConverter"/>
</beans>
```

开启mvc的关键配置是`<mvc:annotation-driven/>`，对应的[NamespaceHandler]是[MvcNamespaceHandler]，代码：
```java
public class MvcNamespaceHandler extends NamespaceHandlerSupport {
	@Override
	public void init() {
		registerBeanDefinitionParser("annotation-driven", new AnnotationDrivenBeanDefinitionParser());
		registerBeanDefinitionParser("default-servlet-handler", new DefaultServletHandlerBeanDefinitionParser());
		registerBeanDefinitionParser("interceptors", new InterceptorsBeanDefinitionParser());
		registerBeanDefinitionParser("resources", new ResourcesBeanDefinitionParser());
		registerBeanDefinitionParser("view-controller", new ViewControllerBeanDefinitionParser());
		registerBeanDefinitionParser("redirect-view-controller", new ViewControllerBeanDefinitionParser());
		registerBeanDefinitionParser("status-controller", new ViewControllerBeanDefinitionParser());
		registerBeanDefinitionParser("view-resolvers", new ViewResolversBeanDefinitionParser());
		registerBeanDefinitionParser("tiles-configurer", new TilesConfigurerBeanDefinitionParser());
		registerBeanDefinitionParser("freemarker-configurer", new FreeMarkerConfigurerBeanDefinitionParser());
		registerBeanDefinitionParser("groovy-configurer", new GroovyMarkupConfigurerBeanDefinitionParser());
		registerBeanDefinitionParser("script-template-configurer", new ScriptTemplateConfigurerBeanDefinitionParser());
		registerBeanDefinitionParser("cors", new CorsBeanDefinitionParser());
	}
}
```

`annotation-driven`的解析由[AnnotationDrivenBeanDefinitionParser]完成，核心代码：
```java
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
```

[AnnotationDrivenBeanDefinitionParser]添加了若干个bean，每个bean都有不同的功能，涉及到的类很多，这里对重要的类进行分析：
- [RequestMappingHandlerMapping的实现](RequestMappingHandlerMapping的实现.md)
- [RequestMappingHandlerAdapter的实现](RequestMappingHandlerAdapter的实现.md)

由于[AnnotationDrivenBeanDefinitionParser]中涉及到的类非常多，所有下面用一个例子分析[AnnotationDrivenBeanDefinitionParser]中涉及到的类的作用，同时也分析常见的Spring MVC注解的实现原理，首先看例子代码整体内容：

文件结构：
![spring-webmvc-test文件结构](../../img/spring-webmvc-test文件结构.png)

主要文件内容：

applicationContext.xml文件：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-4.0.xsd http://www.springframework.org/schema/mvc http://www.springframework.org/schema/mvc/spring-mvc.xsd http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="com.dhf"/>
    <mvc:annotation-driven/>

    <!--解决对象转json问题-->
    <bean class="org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter">
        <property name="messageConverters">
            <list>
                <ref bean="jsonMessageConverter"/>
            </list>
        </property>
    </bean>

    <bean id="jsonMessageConverter"
          class="org.springframework.http.converter.json.MappingJackson2HttpMessageConverter"/>
</beans>
```

model-with-request-mapping.jsp
```jsp
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<html>
<body>
<h1><%= request.getAttribute("demoModelKey3")%></h1>
</body>
</html>
```

index.jsp
```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<body>
${request.demoModelKey}<br>
<br>
</body>
</html>
```

post-form.jsp
```jsp
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<html>
<body>
<form:form action="/app/test/show-post-body" method="post" modelAttribute="userModel">
    <table>
        <tr>
            <td><form:label path="name">Name</form:label></td>
            <td><form:input path="name"/></td>
        </tr>
        <tr>
            <td><form:label path="age">Age</form:label></td>
            <td><form:input path="age"/></td>
        </tr>
        <tr>
            <td colspan="2"><input type="submit" value="Post User"/>
            </td>
        </tr>
    </table>
</form:form>
</body>
</html>
```

show-value.jsp
```jsp
<%@taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<body>
<h1>Session Value</h1>

<c:forEach var="v" items="${values}">
    <table>
        <tr>
            <td>${v}</td>
        </tr>
    </table>
</c:forEach>

</body>
</html>
```

AppController.java
```java
@Controller
@RequestMapping("/app")
@SessionAttributes(value = {"key1", "key2"})
public class AppController {
    @RequestMapping(value = "index", method = RequestMethod.GET)
    public String index(@ModelAttribute("user") User user) {
        user.setName("test");
        return "index";
    }

    @ResponseBody
    @RequestMapping(value = "/rest/index", method = RequestMethod.GET)
    public String restIndex(@ModelAttribute("user") User user) {
        user.setName("test");
        return "index";
    }

    //---------------------------------------------------------------------
    // 开始SessionAttributes、RequestParam注解测试
    //---------------------------------------------------------------------

    @RequestMapping(value = "/add-session-value")
    public ModelAndView addSessionValue() {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("forward:show-value?a=1&b=2");
        modelAndView.addObject("key1", "key1Value");
        modelAndView.addObject("key2", "key2Value");
        return modelAndView;
    }

    @GetMapping(value = "/show-value", params = {"a=1", "c=3"})
    public ModelAndView showSessionValue1(@ModelAttribute("key1") String key1,
                                         @ModelAttribute("key2") String key2,
                                         @RequestHeader("User-Agent") String userAgent,
                                         @CookieValue("JSESSIONID") String jSessionId,
                                         @RequestParam String a,
                                         @RequestParam String b) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("show-value");
        modelAndView.addObject("values", Arrays.asList(key1, key2, userAgent, jSessionId, a, b));
        return modelAndView;
    }

    //---------------------------------------------------------------------
    // 结束SessionAttributes、RequestParam注解测试
    //---------------------------------------------------------------------


    //---------------------------------------------------------------------
    // 开始ResponseBody、ModelAttribute、PathVariable、MatrixVariable注解测试
    //---------------------------------------------------------------------

    // 这里只在方法上添加了ModelAttribute注解，这将使得当前类上所有请求方法在被执行前都会调用一次nameModelReturnString方法，调用的效果是，会创建一个名叫
    // demoModelKey1的model属性，值为nameModelReturnString方法的返回值，这里nameModelReturnString方法的返回值是RequestParam注解的值，也就是请求路径中的查询参数test的值，
    // 如/app/index?test=abc
    @ModelAttribute("demoModelKey1")
    public String nameModelReturnString(
            @RequestParam(value = "test", required = false) String test) {
        return test;
    }

    // 这里和nameModelReturnString方法做的事差不多，只不过添加model的方法变成了直接设置到参数model对象上，当前类的所有请求方法在执行前都会调用nameModelReturnString和nameModelReturnVoid方法，
    // 按照声明顺序调用
    @ModelAttribute
    public void nameModelReturnVoid(
            @RequestParam(value = "test", required = false) String test,
            Model model) {
        model.addAttribute("demoModelKey2", test);
        model.addAttribute("age", 123);
    }

    // 这里也是做的差不多的事，model属性的名称等于返回值的类型名，这里就是string
    @ModelAttribute
    public String nameModelReturnString() {
        return "dhf";
    }

    // 这里就和上面的只有ModelAttribute注解的方法不一样了，该方法还有一个RequestMapping注解，所以这也是一个请求方法，该方法的特点是，会
    // 创建一个key为demoModelKey3的model，值为方法的返回值，并且最后还会访问RequestMapping注解的值对应的视图，视图路径是请求的相对路径，
    // 既/app/model-with-request-mapping，所以效果是，浏览器访问/app/model-with-request-mapping，该请求被执行，创建一个model，并返回
    // 视图WEB-INF/jsp/app/model-with-request-mapping.jsp
    @ModelAttribute(value = "demoModelKey3")
    @RequestMapping(value = "/model-with-request-mapping")
    public String nameModelWithRequestMapping() {
        return "demoModelValue3";
    }

    // 这里的作用是添加一个名叫userModel的User到model，和showUser方法相呼应，showUser方法也有一个ModelAttribute注解，值为userModel，
    // 这将使得showUser方法被调用时user参数被赋值为这里创建的user
    @ModelAttribute("userModel")
    public User nameModelReturnUser() {
        User user = new User();
        // 这里设置了customs，在showUser中不用再设置了，同时这里也只设置了customs，没有设置name和age，允许测试调用http://localhost:8080/app/show-post-form
        // 后填写表单提交后可以发现，showUser的userModel值会同时拥有这里设置的customs属性和页面设置的name、age属性
        user.setCustoms(new ArrayList<>(Arrays.asList("demo1", "demo2")));
        return user;
    }

    // 必须添加一个user到model，否则post-form.jsp报错
    @GetMapping(value = "/show-post-form")
    public String showPostForm(@ModelAttribute("userModel") User user, @ModelAttribute("age") Integer age) {
        user.setAge(age);
        return "post-form";
    }

    @ResponseBody
    @RequestMapping(value = "/{test}/show-post-body", method = RequestMethod.POST)
    public User showUser(@ModelAttribute("userModel") User userModel, @PathVariable String test, @ModelAttribute("string") String string) {
        userModel.getCustoms().addAll(Arrays.asList(test, string));
        return userModel;
    }

    // MatrixVariable注解需要和PathVariable注解一块使用，只有跟在PathVariable所表示的请求路径参数后面的MatrixVariable才有效，既
    // /matrix/test;a=b/example，这样MatrixVariable的注解才能正常工作，如果是/matrix/test/example;a=b则MatrixVariable注解的值获取不到
    @RequestMapping(value = "matrix/{type}/example", method = RequestMethod.GET)
    public String matrixVariable1(@PathVariable String type, @MatrixVariable(required = false) String a) {
        System.out.println(String.format("type: %s", type));
        System.out.println(String.format("a: %s", a));
        return "index";
    }

    /*
     MatrixVariable注解的其他使用形式
     下面输入请求http://localhost:8080/app/matrix/test;a=1;b=2，将打印
     type: test
     a: 1
     b: 2
     */
    @RequestMapping(value = "matrix/{type}", method = RequestMethod.GET)
    public String matrixVariable2(@PathVariable String type, @MatrixVariable String a, @MatrixVariable String b) {
        System.out.println(String.format("type: %s", type));
        System.out.println(String.format("a: %s", a));
        System.out.println(String.format("b: %s", b));
        return "index";
    }

    /*
    MatrixVariable注解的值是和请求路径中的变量绑定的，下面演示如何进行绑定，输入请求
    http://localhost:8080/app/employee/Steve;id=101/dept/Sales;id=2，下面的方法将输出
    empId: 101
    deptId: 2
     */
    @RequestMapping("/employee/{empName}/dept/{deptName}")
    public String matrixVariable3(@MatrixVariable(value = "id", pathVar = "empName") int empId,
                             @MatrixVariable(value = "id", pathVar = "deptName") int deptId) {

        System.out.println(String.format("empId: %s", empId));
        System.out.println(String.format("deptId: %s", deptId));
        return "index";
    }

    /*
    MatrixVariable还可以用map接收，输入请求
    http://localhost:8080/app/employee/map/Steve;id=101;salary=2500/dept/Sales;id=2;type=internal，下面的方法将输出
    allMatrixVars: {id=101, salary=2500, type=internal}
    empMatrixVar: {id=101, salary=2500}
    deptMatrixVar: {id=2, type=internal}
     */
    @RequestMapping("/employee/map/{empName}/dept/{deptName}")
    public String matrixVariable4(
            @MatrixVariable Map<String, String> allMatrixVars,
            @MatrixVariable(pathVar="empName") Map<String, String> empMatrixVar,
            @MatrixVariable(pathVar="deptName") Map<String, String> deptMatrixVar) {

        System.out.println(String.format("allMatrixVars: %s", allMatrixVars));
        System.out.println(String.format("empMatrixVar: %s", empMatrixVar));
        System.out.println(String.format("deptMatrixVar: %s", deptMatrixVar));
        return "index";
    }

    //---------------------------------------------------------------------
    // 结束ResponseBody、ModelAttribute、PathVariable、MatrixVariable注解测试
    //---------------------------------------------------------------------
}
```

上面的例子涉及到了常用的Spring MVC的注解，这里先分析一个比较简单的例子的实现原理，代码：
```java
// 这里的作用是添加一个属性到model，属性的名称默认等于返回值的类型名，这里就是string
@ModelAttribute
public String nameModelReturnString() {
    return "dhf";
}

// 这里的作用也是添加一个名叫userModel的User到model，和showUser方法相呼应，showUser方法也有一个ModelAttribute注解，值为userModel，
// 这将使得showUser方法被调用时user参数被赋值为这里创建的user
@ModelAttribute("userModel")
public User nameModelReturnUser() {
    User user = new User();
    // 这里设置了customs，在showUser中不用再设置了，同时这里也只设置了customs，没有设置name和age，允许测试调用http://localhost:8080/app/show-post-form
    // 后填写表单提交后可以发现，showUser的userModel值会同时拥有这里设置的customs属性和页面设置的name、age属性
    user.setCustoms(new ArrayList<>(Arrays.asList("demo1", "demo2")));
    return user;
}

// 必须添加一个user到model，否则post-form.jsp报错
@GetMapping(value = "/show-post-form")
public String showPostForm(@ModelAttribute("userModel") User user, @ModelAttribute("age") Integer age) {
    user.setAge(age);
    return "post-form";
}

@ResponseBody
@RequestMapping(value = "/{test}/show-post-body", method = RequestMethod.POST)
public User showUser(@ModelAttribute("userModel") User userModel, @PathVariable String test, @ModelAttribute("string") String string) {
    userModel.getCustoms().addAll(Arrays.asList(test, string));
    return userModel;
}
```

在浏览器输入`http://localhost:8080/app/show-post-form`，页面将会显示一个表单，填写表单点击按钮后，浏览器将发送POST请求，并在请求返回后显示填写的表单的内容，由于`nameModelReturnString()`和`nameModelReturnUser()`方法的存在，页面上显示的`customs`内容为`["demo1","demo2","test","dhf"]`

下面对这个简单例子涉及到的注解进行分析，首先是[AppController]类上的[Controller]注解，该注解标记指定类为一个bean（这一过程可以看笔记[ComponentScans注解的实现过程.md](../ComponentScans注解的实现过程.md)），同时也是一个handler，能够处理某些请求，[RequestMappingHandlerMapping]在其方法`isHandler()`中认为带有该注解的bean是一个handler，代码：
```java
@Override
protected boolean isHandler(Class<?> beanType) {
    // 如果类上存在下面两个注解中的一个则认为是handler
    return (AnnotatedElementUtils.hasAnnotation(beanType, Controller.class) ||
            AnnotatedElementUtils.hasAnnotation(beanType, RequestMapping.class));
}
```

根据笔记[如何实现请求的分发和响应](如何实现请求的分发和响应.md)的内容可知，在[DispatcherServlet]分发请求时，会获取[HandlerExecutionChain]对象，该对象包含了能够执行请求的handler对象，同时还包含了针对该handler的[HandlerInterceptor]，[HandlerInterceptor]能够实现在处理请求的不同阶段执行方法，代码：
```java
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    //...
    HandlerExecutionChain mappedHandler = null;
    mappedHandler = getHandler(processedRequest);
    //...
}

protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
    if (this.handlerMappings != null) {
        for (HandlerMapping hm : this.handlerMappings) {
            if (logger.isTraceEnabled()) {
                logger.trace(
                        "Testing handler map [" + hm + "] in DispatcherServlet with name '" + getServletName() + "'");
            }
            HandlerExecutionChain handler = hm.getHandler(request);
            if (handler != null) {
                return handler;
            }
        }
    }
    return null;
}
```

上面用到的`handlerMappings`的初始化在[DispatcherServlet]的`initHandlerMappings`方法，代码：
```java
private void initHandlerMappings(ApplicationContext context) {
    this.handlerMappings = null;

    if (this.detectAllHandlerMappings) {
        // 获取所有实现了HandlerMapping接口的bean
        // Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
        Map<String, HandlerMapping> matchingBeans =
                BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerMappings = new ArrayList<>(matchingBeans.values());
            // We keep HandlerMappings in sorted order.
            AnnotationAwareOrderComparator.sort(this.handlerMappings);
        }
    }
    else {
        try {
            HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
            this.handlerMappings = Collections.singletonList(hm);
        }
        catch (NoSuchBeanDefinitionException ex) {
            // Ignore, we'll add a default HandlerMapping later.
        }
    }

    // Ensure we have at least one HandlerMapping, by registering
    // a default HandlerMapping if no other mappings are found.
    if (this.handlerMappings == null) {
        this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
        if (logger.isDebugEnabled()) {
            logger.debug("No HandlerMappings found in servlet '" + getServletName() + "': using default");
        }
    }
}
```

默认情况下`detectAllHandlerMappings`为true，所以所有实现了[HandlerMapping]接口的bean都会被添加到`handlerMappings`中，[AnnotationDrivenBeanDefinitionParser]类的`parse()`方法添加的[RequestMappingHandlerMapping]就实现了该方法，即使`detectAllHandlerMappings`为false，`initHandlerMappings()`方法也会获取beanName为`HANDLER_MAPPING_BEAN_NAME`的bean添加到`handlerMappings`，而[AnnotationDrivenBeanDefinitionParser]类在添加[RequestMappingHandlerMapping]作为bean时，设置的beanName就是`HANDLER_MAPPING_BEAN_NAME`，代码：
```java
readerContext.getRegistry().registerBeanDefinition(HANDLER_MAPPING_BEAN_NAME , handlerMappingDef);
```

这样当分发请求时获取的[HandlerExecutionChain]就来自[RequestMappingHandlerMapping]类，对于[RequestMappingHandlerMapping]类如何根据[RequestMapping]注解创建[HandlerExecutionChain]的，可以看笔记[RequestMappingHandlerMapping的实现](RequestMappingHandlerMapping的实现.md)，简单来说就是，[RequestMappingHandlerMapping]在其`afterPropertiesSet()`方法中遍历了所有bean并调用`isHandler()`方法过滤不满足条件的bean，对于满足条件的bean，遍历每个bean的所有方法，根据方法及bean上的[RequestMapping]注解，创建[RequestMappingInfo]对象，该对象包含了[RequestMapping]注解的所有信息，而bean本身被认为是handler保存下来，在创建[HandlerExecutionChain]时，[RequestMappingHandlerMapping]从[RequestMappingInfo]中获取handler，并添加需要的[HandlerInterceptor]到[HandlerExecutionChain]对象

[DispatcherServlet]的`doDispatch()`方法在获取到[HandlerExecutionChain]后，并没有直接调用handler的方法处理请求，因为对于handler可能是任意类型的对象，处理请求的方法也各不相同，所以[DispatcherServlet]在获取到[HandlerExecutionChain]后，又获取了[HandlerAdapter]，顾名思义，[HandlerAdapter]屏蔽了handler之间的差异，一个[HandlerAdapter]能够针对某种类型的handler实现请求的处理，获取[HandlerAdapter]的过程很简单，遍历所有的[HandlerAdapter]，找到适配当前handler的[HandlerAdapter]之后返回，代码：
```java
protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    //...
    HandlerExecutionChain mappedHandler = null;
    mappedHandler = getHandler(processedRequest);

    HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());
    //...
}

protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
    if (this.handlerAdapters != null) {
        for (HandlerAdapter ha : this.handlerAdapters) {
            if (logger.isTraceEnabled()) {
                logger.trace("Testing handler adapter [" + ha + "]");
            }
            if (ha.supports(handler)) {
                return ha;
            }
        }
    }
    throw new ServletException("No adapter for handler [" + handler +
            "]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
}
```

上面用到的`handlerAdapters`的初始化和之前说到的`handlerMappings`的初始化一样，也是遍历所有实现了[HandlerAdapter]接口的bean，或者获取beanName为`HANDLER_ADAPTER_BEAN_NAME`的bean，而[AnnotationDrivenBeanDefinitionParser]类的`parse()`方法就添加了实现了[HandlerAdapter]接口的[RequestMappingHandlerAdapter]类作为bean，并设置beanName为`HANDLER_ADAPTER_BEAN_NAME`

所以可以看到，[Controller]让一个类成为了bean，能是也满足了成功handler的条件，而[RequestMapping]注解配置了该handler所能处理的方法，[RequestMappingHandlerMapping]类通过解析[RequestMapping]注解实现了请求路径和handler的映射，在请求到来时，根据请求路径返回对应的标记了[Controller]注解的bean作为handler，从而实现了请求的基于注解的请求分发，请求分发的其他细节可以看笔记[如何实现请求的分发和响应](如何实现请求的分发和响应.md)

上面分析了[Controller]和[RequestMapping]注解是如何帮组SpringMVC实现请求分发的，这是SpringMVC的基本功能，在这基础之上，SpringMVC还提供了丰富的注解实现请求执行过程中的其他功能，下面再逐个分析例子中用到的注解：
- [ModelAttribute]：该注解如果标记在方法上，则能够在controller处理请求之前，添加属性到model，如果标记在处理请求的方法参数上，则能够在处理请求的方法中访问model中的对应属性，该注解的实现原理是：
  在[DispatcherServlet]从[RequestMappingHandlerMapping]获取到[HandlerExecutionChain]后，会使用[RequestMappingHandlerAdapter]作为[HandlerAdapter]处理请求，而[RequestMappingHandlerAdapter]的`handler()`方法实现如下：
  ```java
  public final ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler)
          throws Exception {

      return handleInternal(request, response, (HandlerMethod) handler);
  }

  protected ModelAndView handleInternal(HttpServletRequest request,
          HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

      ModelAndView mav;
      // 判断当前请求的method是否被支持，如果requireSession为true，还会判断当前请求是否存在session
      checkRequest(request);

      // Execute invokeHandlerMethod in synchronized block if required.
      // 如果synchronizeOnSession为true则在调用invokeHandlerMethod执行请求时需要加锁
      if (this.synchronizeOnSession) {
          HttpSession session = request.getSession(false);
          if (session != null) {
              Object mutex = WebUtils.getSessionMutex(session);
              synchronized (mutex) {
                  mav = invokeHandlerMethod(request, response, handlerMethod);
              }
          }
          else {
              // No HttpSession available -> no mutex necessary
              mav = invokeHandlerMethod(request, response, handlerMethod);
          }
      }
      else {
          // No synchronization on session demanded at all...
          mav = invokeHandlerMethod(request, response, handlerMethod);
      }

      // 如果response中没有配置Cache-Control
      if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
          // 如果处理当前请求的controller配置了SessionAttributes注解，添加相应的缓存相关的header用于设置SessionAttributes的有效时间，cacheSecondsForSessionAttributeHandlers参数表示缓存的有效时间
          if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
              applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
          }
          else {
              // 如果没有配置SessionAttributes注解，则根据cacheControl属性或cacheSeconds属性对缓存header进行配置，同时对vary header进行设置
              prepareResponse(response);
          }
      }

      return mav;
  }
  ```

  可以看到，真正处理请求的方法是`invokeHandlerMethod()`，该方法包括了执行请求前model的准备、请求的执行和请求结果的处理，代码：
  ```java
  @Nullable
  protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
          HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

      // ServletWebRequest对象封装了request和response
      ServletWebRequest webRequest = new ServletWebRequest(request, response);
      try {
          // WebDataBinderFactory能够创建WebDataBinder对象，而WebDataBinder对象能够为指定的对象设置属性
          WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
          // ModelFactory能够在调用执行请求的方法之前准备好Model，并且通过ModelFactory能够更新Model
          ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

          // createInvocableHandlerMethod方法直接返回一个ServletInvocableHandlerMethod实例，ServletInvocableHandlerMethod对象
          // 代表的就是即将执行请求的方法
          ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(handlerMethod);
          // 为invocableMethod设置各种属性
          if (this.argumentResolvers != null) {
              invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
          }
          if (this.returnValueHandlers != null) {
              invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
          }
          invocableMethod.setDataBinderFactory(binderFactory);
          invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

          // ModelAndViewContainer对象持有和创建了真正的Model对象，Model实际上就是一个map
          ModelAndViewContainer mavContainer = new ModelAndViewContainer();
          // 添加request中名字为DispatcherServlet.INPUT_FLASH_MAP_ATTRIBUTE的属性，既FlashMap对象
          mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
          // 保存session的属性到mavContainer，并调用带有ModelAttribute注解的方法，获取model的属性
          modelFactory.initModel(webRequest, mavContainer, invocableMethod);
          // 当是redirect请求时，或者mavContainer的redirectModel为空是，是否使用defaultModel
          mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

          // createAsyncWebRequest方法默认返回StandardServletAsyncWebRequest实例
          AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
          asyncWebRequest.setTimeout(this.asyncRequestTimeout);

          // getAsyncManager方法默认返回WebAsyncManager实例，并将该实例保存到request的WEB_ASYNC_MANAGER_ATTRIBUTE属性
          WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
          asyncManager.setTaskExecutor(this.taskExecutor);
          asyncManager.setAsyncWebRequest(asyncWebRequest);
          asyncManager.registerCallableInterceptors(this.callableInterceptors);
          asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

          // 如果设置了并发调用的返回值
          if (asyncManager.hasConcurrentResult()) {
              Object result = asyncManager.getConcurrentResult();
              mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
              asyncManager.clearConcurrentResult();
              if (logger.isDebugEnabled()) {
                  logger.debug("Found concurrent result value [" + result + "]");
              }
              // wrapConcurrentResult方法返回一个ConcurrentResultHandlerMethod实例
              invocableMethod = invocableMethod.wrapConcurrentResult(result);
          }

          // 执行方法，并处理方法返回值
          invocableMethod.invokeAndHandle(webRequest, mavContainer);
          if (asyncManager.isConcurrentHandlingStarted()) {
              return null;
          }

          // 如果有必要，创建ModelAndView对象
          return getModelAndView(mavContainer, modelFactory, webRequest);
      }
      finally {
          webRequest.requestCompleted();
      }
  }
  ```
  
  针对[ModelAttribute]注解，需要关注的是`invokeHandlerMethod()`方法中调用的`getModelFactory()`方法，该方法返回[ModelFactory]对象，对于[ModelFactory]的作用，可以看笔记[ModelFactory的实现](ModelFactory的实现.md)，`getModelFactory()`方法遍历每个满足条件的method，调用`createModelAttributeMethod()`方法创建[InvocableHandlerMethod]对象，该对象持有指定的方法和该方法所属bean，同时还持有多个[HandlerMethodArgumentResolver]，能够对各种类型的方法参数进行解析，SpringMVC中方法参数上的各种注解就是不同的[HandlerMethodArgumentResolver]提供支持的，[InvocableHandlerMethod]类的实现可以看笔记[ServletInvocableHandlerMethod的实现](ServletInvocableHandlerMethod的实现.md)，而[HandlerMethodArgumentResolver]的实现在笔记[HandlerMethodArgumentResolver的实现](HandlerMethodArgumentResolver的实现.md)

  创建完[ModelFactory]对象后，`invokeHandlerMethod()`方法为将执行请求的方法创建[ServletInvocableHandlerMethod]对象，[ServletInvocableHandlerMethod]继承自[InvocableHandlerMethod]，在[InvocableHandlerMethod]的基础上提供了处理请求执行结果的逻辑，之后`invokeHandlerMethod()`方法调用`modelFactory.initModel(webRequest, mavContainer, invocableMethod);`初始化了model数据，这一过程在笔记[ModelFactory的实现](ModelFactory的实现.md)中有介绍，最后就是调用`invocableMethod.invokeAndHandle(webRequest, mavContainer);`执行请求，`invocableMethod`是[ServletInvocableHandlerMethod]类型的，该类的实现可以看笔记[ServletInvocableHandlerMethod的实现](ServletInvocableHandlerMethod的实现.md)

[AppController]: aaa
[AnnotationDrivenBeanDefinitionParser]: aaa
[RequestMappingHandlerMapping]: aaa
[RequestMapping]: aaa
[RequestMappingInfo]: aaa
[DispatcherServlet]: aaa
[HandlerExecutionChain]: aaa