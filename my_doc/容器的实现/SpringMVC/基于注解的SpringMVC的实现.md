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

`annotation-driven`的解析由[AnnotationDrivenBeanDefinitionParser]完成，











下面是例子：

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

    @RequestMapping(value="/add-session-value")
    public ModelAndView addSessionValue() {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("forward:show-value?a=1&b=2");
        modelAndView.addObject("key1", "key1Value");
        modelAndView.addObject("key2", "key2Value");
        return modelAndView;
    }

    @GetMapping(value="/show-value")
    public ModelAndView showSessionValue(@ModelAttribute("key1") String key1,
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
    // 开始ResponseBody、ModelAttribute、PathVariable注解测试
    //---------------------------------------------------------------------

    // 这里只在方法上添加了ModelAttribute注解，这将使得当前类上所有请求方法在被执行前都会调用一次nameModelReturnString方法，调用的效果是，会创建一个名叫
    // name的model属性，值为nameModelReturnString方法的返回值，这里nameModelReturnString方法的返回值是RequestParam注解的值，也就是请求路径中的查询参数test的值，
    // 如/app/index?test=abc
    @ModelAttribute("demoModelKey1")
    public String nameModelReturnString(
            @RequestParam(value = "test", required = false) String test){
        return test;
    }

    // 这里和nameModelReturnString方法做的事差不多，只不过添加model的方法变成了直接设置到参数model对象上，当前类的所有请求方法在执行前都会调用nameModelReturnString和nameModelReturnVoid方法，
    // 按照声明顺序调用
    @ModelAttribute
    public void nameModelReturnVoid(
            @RequestParam(value = "test", required = false) String test,
            Model model){
        model.addAttribute("demoModelKey2", test);
        model.addAttribute("age", 123);
    }

    // 这里也是做的差不多的事，model属性的名称等于返回值的类型名，这里就是string
    @ModelAttribute
    public String nameModelReturnString(){
        return "dhf";
    }

    // 这里就和上面的只有ModelAttribute注解的方法不一样了，该方法还有一个RequestMapping注解，所以这也是一个请求方法，该方法的特点是，会
    // 创建一个key为demoModelKey的model，值为方法的返回值，并且最后还会访问RequestMapping注解的值对应的视图，视图路径是请求的相对路径，
    // 既/app/model-with-request-mapping，所以效果是，浏览器访问/app/model-with-request-mapping，该请求被执行，创建一个model，并返回
    // 视图WEB-INF/jsp/app/model-with-request-mapping.jsp
    @ModelAttribute(value = "demoModelKey3")
    @RequestMapping(value = "/model-with-request-mapping")
    public String nameModelWithRequestMapping(){
        return "demoModelValue3";
    }

    // 这里的作用是添加一个名叫userModel的User到model，和showUser方法相呼应，showUser方法也有一个ModelAttribute注解，值为userModel，
    // 这将使得showUser方法被调用时user参数被赋值为这里创建的user
    @ModelAttribute("userModel")
    public User nameModelReturnUser(){
        User user = new User();
        // 这里设置了customs，在showUser中不用再设置了，同时这里也只设置了customs，没有设置name和age，允许测试调用http://localhost:8080/app/show-post-form
        // 后填写表单提交后可以发现，showUser的userModel值会同时拥有这里设置的customs属性和页面设置的name、age属性
        user.setCustoms(new ArrayList<>(Arrays.asList("demo1", "demo2")));
        return user;
    }

    // 必须添加一个user到model，否则post-form.jsp报错
    @GetMapping(value="/show-post-form")
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

    //---------------------------------------------------------------------
    // 结束ResponseBody、ModelAttribute、PathVariable注解测试
    //---------------------------------------------------------------------
}
```


[AppController]: aaa