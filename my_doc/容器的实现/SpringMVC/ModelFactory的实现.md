[ModelFactory]类在[RequestMappingHandlerAdapter]类的`invokeHandlerMethod()`方法处理请求时提供了model的初始化相关操作，代码：
```java
@Nullable
protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
    HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {
    //...
    // ModelFactory能够在调用执行请求的方法之前准备好Model，并且通过ModelFactory能够更新Model
    ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);
    //...

    // ModelAndViewContainer对象持有和创建了真正的Model对象，Model实际上就是一个map
    ModelAndViewContainer mavContainer = new ModelAndViewContainer();
    //...
    // 保存session的属性到mavContainer，并调用带有ModelAttribute注解的方法，获取model的属性
    modelFactory.initModel(webRequest, mavContainer, invocableMethod);
    //...
}
```

[ModelFactory]对象的初始化在`getModelFactory()`方法，代码：
```java
// getModelFactory方法中用到的方法筛选器
public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
((AnnotationUtils.findAnnotation(method, RequestMapping.class) == null) &&
(AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null));

private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
    // SessionAttributesHandler能够解析handler上的SessionAttributes注解，并将该注解配置的属性保存到sessionAttributeStore
    // 对象上，SessionAttributesHandler通过sessionAttributeStore可以实现session属性的读写，sessionAttributeStore的默认实现
    // 是DefaultSessionAttributeStore，实现原理就是将属性保存到request中，scope设置为WebRequest.SCOPE_SESSION，即：
    // request.setAttribute(storeAttributeName, attributeValue, WebRequest.SCOPE_SESSION);
    SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
    Class<?> handlerType = handlerMethod.getBeanType();
    Set<Method> methods = this.modelAttributeCache.get(handlerType);
    if (methods == null) {
        // 获取所有没有RequestMapping注解但是有ModelAttribute注解的方法
        methods = MethodIntrospector.selectMethods(handlerType, MODEL_ATTRIBUTE_METHODS);
        this.modelAttributeCache.put(handlerType, methods);
    }
    List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
    // Global methods first
    // modelAttributeAdviceCache的key为所有实现了ControllerAdvice接口的bean，value为该bean上没有RequestMapping注解但是有ModelAttribute注解的方法集合
    this.modelAttributeAdviceCache.forEach((clazz, methodSet) -> {
        if (clazz.isApplicableToBeanType(handlerType)) {
            Object bean = clazz.resolveBean();
            for (Method method : methodSet) {
                // 为每个方法都创建一个InvocableHandlerMethod对象，注意这里的InvocableHandlerMethod对象和createInitBinderMethod方法中创建的InvocableHandlerMethod对象
                // 传入的HandlerMethodArgumentResolverComposite不同，用的是argumentResolvers
                attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
            }
        }
    });
    // 遍历当前bean上没有RequestMapping注解但是有ModelAttribute注解的方法，同样为这些方法创建InvocableHandlerMethod对象
    for (Method method : methods) {
        Object bean = handlerMethod.getBean();
        attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
    }
    return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
}
```

`getModelFactory()`方法首先获取了当前方法的[SessionAttributesHandler]对象，代码：
```java
private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
  Class<?> handlerType = handlerMethod.getBeanType();
  SessionAttributesHandler sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
  if (sessionAttrHandler == null) {
    synchronized (this.sessionAttributesHandlerCache) {
      sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
      if (sessionAttrHandler == null) {
        sessionAttrHandler = new SessionAttributesHandler(handlerType, sessionAttributeStore);
        this.sessionAttributesHandlerCache.put(handlerType, sessionAttrHandler);
      }
    }
  }
  return sessionAttrHandler;
}
```

如果缓存中不存在当前方法的[SessionAttributesHandler]对象，则创建一个，[SessionAttributesHandler]类的实现很简单，代码：
```java
public class SessionAttributesHandler {

	// 保存从SessionAttributes注解中获取到的属性名称
	private final Set<String> attributeNames = new HashSet<>();

	// 保存从SessionAttributes注解中获取到的属性类型
	private final Set<Class<?>> attributeTypes = new HashSet<>();

	// 保存已知的当前session中存在的属性名称
	private final Set<String> knownAttributeNames = Collections.newSetFromMap(new ConcurrentHashMap<>(4));

	// session的属性存储介质
	private final SessionAttributeStore sessionAttributeStore;

	public SessionAttributesHandler(Class<?> handlerType, SessionAttributeStore sessionAttributeStore) {
		Assert.notNull(sessionAttributeStore, "SessionAttributeStore may not be null");
		this.sessionAttributeStore = sessionAttributeStore;

		// 获取类上的SessionAttributes注解，将注解的值保存下来
		SessionAttributes ann = AnnotatedElementUtils.findMergedAnnotation(handlerType, SessionAttributes.class);
		if (ann != null) {
			Collections.addAll(this.attributeNames, ann.names());
			Collections.addAll(this.attributeTypes, ann.types());
		}
		this.knownAttributeNames.addAll(this.attributeNames);
	}
  
	public boolean hasSessionAttributes() {
		return (!this.attributeNames.isEmpty() || !this.attributeTypes.isEmpty());
	}
  
	public boolean isHandlerSessionAttribute(String attributeName, Class<?> attributeType) {
		Assert.notNull(attributeName, "Attribute name must not be null");
		if (this.attributeNames.contains(attributeName) || this.attributeTypes.contains(attributeType)) {
			this.knownAttributeNames.add(attributeName);
			return true;
		}
		else {
			return false;
		}
	}
  
	public void storeAttributes(WebRequest request, Map<String, ?> attributes) {
		attributes.forEach((name, value) -> {
			if (value != null && isHandlerSessionAttribute(name, value.getClass())) {
				this.sessionAttributeStore.storeAttribute(request, name, value);
			}
		});
	}
  
	public Map<String, Object> retrieveAttributes(WebRequest request) {
		Map<String, Object> attributes = new HashMap<>();
		for (String name : this.knownAttributeNames) {
			Object value = this.sessionAttributeStore.retrieveAttribute(request, name);
			if (value != null) {
				attributes.put(name, value);
			}
		}
		return attributes;
	}
  
	public void cleanupAttributes(WebRequest request) {
		for (String attributeName : this.knownAttributeNames) {
			this.sessionAttributeStore.cleanupAttribute(request, attributeName);
		}
	}
  
	@Nullable
	Object retrieveAttribute(WebRequest request, String attributeName) {
		return this.sessionAttributeStore.retrieveAttribute(request, attributeName);
	}

}
```

[SessionAttributesHandler]类中用到的[SessionAttributeStore]的实现是[DefaultSessionAttributeStore]，该类保存session属性的代码也很简单，代码：
```java
public class DefaultSessionAttributeStore implements SessionAttributeStore {

	private String attributeNamePrefix = "";

	public void setAttributeNamePrefix(@Nullable String attributeNamePrefix) {
		this.attributeNamePrefix = (attributeNamePrefix != null ? attributeNamePrefix : "");
	}

	@Override
	public void storeAttribute(WebRequest request, String attributeName, Object attributeValue) {
		Assert.notNull(request, "WebRequest must not be null");
		Assert.notNull(attributeName, "Attribute name must not be null");
		Assert.notNull(attributeValue, "Attribute value must not be null");
		String storeAttributeName = getAttributeNameInSession(request, attributeName);
    // 属性直接保存到request中，scope为WebRequest.SCOPE_SESSION
		request.setAttribute(storeAttributeName, attributeValue, WebRequest.SCOPE_SESSION);
	}

	@Override
	@Nullable
	public Object retrieveAttribute(WebRequest request, String attributeName) {
		Assert.notNull(request, "WebRequest must not be null");
		Assert.notNull(attributeName, "Attribute name must not be null");
		String storeAttributeName = getAttributeNameInSession(request, attributeName);
		return request.getAttribute(storeAttributeName, WebRequest.SCOPE_SESSION);
	}

	@Override
	public void cleanupAttribute(WebRequest request, String attributeName) {
		Assert.notNull(request, "WebRequest must not be null");
		Assert.notNull(attributeName, "Attribute name must not be null");
		String storeAttributeName = getAttributeNameInSession(request, attributeName);
		request.removeAttribute(storeAttributeName, WebRequest.SCOPE_SESSION);
	}
  
	protected String getAttributeNameInSession(WebRequest request, String attributeName) {
		return this.attributeNamePrefix + attributeName;
	}

}
```

request保存属性的代码：
```java
public void setAttribute(String name, Object value, int scope) {
  if (scope == SCOPE_REQUEST) {
    if (!isRequestActive()) {
      throw new IllegalStateException(
          "Cannot set request attribute - request is not active anymore!");
    }
    this.request.setAttribute(name, value);
  }
  else {
    // 这里获取session，如果没有则创建一个，如果用的是tomcat，则sessionId为JSESSIONID，值为随机数+时间+jvmid
    HttpSession session = obtainSession();
    this.sessionAttributesToUpdate.remove(name);
    session.setAttribute(name, value);
  }
}
```

创建完[SessionAttributesHandler]对象之后，`getModelFactory()`方法遍历了所有实现了[ControllerAdvice]接口的bean上没有[RequestMapping]注解但是有[ModelAttribute]注解的方法，和handler上没有[RequestMapping]注解但是有[ModelAttribute]注解的方法，为这些方法创建了[InvocableHandlerMethod]对象，用于创建[ModelFactory]对象，对于[InvocableHandlerMethod]的实现，可以看笔记[ServletInvocableHandlerMethod的实现](ServletInvocableHandlerMethod的实现.md)

最后是[ModelFactory]的代码：
```java
public final class ModelFactory {

	private static final Log logger = LogFactory.getLog(ModelFactory.class);

	private final List<ModelMethod> modelMethods = new ArrayList<>();

	private final WebDataBinderFactory dataBinderFactory;

	private final SessionAttributesHandler sessionAttributesHandler;

	public ModelFactory(@Nullable List<InvocableHandlerMethod> handlerMethods,
			WebDataBinderFactory binderFactory, SessionAttributesHandler attributeHandler) {

		if (handlerMethods != null) {
			for (InvocableHandlerMethod handlerMethod : handlerMethods) {
				// ModelMethod类的作用是判断方法参数的ModelAttribute注解的值是否在ModelAndViewContainer中存在
				this.modelMethods.add(new ModelMethod(handlerMethod));
			}
		}
		this.dataBinderFactory = binderFactory;
		this.sessionAttributesHandler = attributeHandler;
	}
  
	public void initModel(NativeWebRequest request, ModelAndViewContainer container, HandlerMethod handlerMethod)
			throws Exception {

		Map<String, ?> sessionAttributes = this.sessionAttributesHandler.retrieveAttributes(request);
		// 添加session的属性到container的model属性集合中
		container.mergeAttributes(sessionAttributes);
		// 调用有ModelAttribute注解的方法，添加其返回值到container的model属性集合中
		invokeModelAttributeMethods(request, container);

		// findSessionAttributeArguments方法返回需要的session属性名称，这里判断这些属性是否在container中存在
		for (String name : findSessionAttributeArguments(handlerMethod)) {
			// 如果属性名称在container中不存在则尝试调用sessionAttributesHandler.retrieveAttribute获取属性值，默认实现是从request的
			// scope为session的参数中获取
			if (!container.containsAttribute(name)) {
				Object value = this.sessionAttributesHandler.retrieveAttribute(request, name);
				if (value == null) {
					throw new HttpSessionRequiredException("Expected session attribute '" + name + "'", name);
				}
				container.addAttribute(name, value);
			}
		}
	}

	private void invokeModelAttributeMethods(NativeWebRequest request, ModelAndViewContainer container)
			throws Exception {

		while (!this.modelMethods.isEmpty()) {
			InvocableHandlerMethod modelMethod = getNextModelMethod(container).getHandlerMethod();
			ModelAttribute ann = modelMethod.getMethodAnnotation(ModelAttribute.class);
			Assert.state(ann != null, "No ModelAttribute annotation");
			if (container.containsAttribute(ann.name())) {
				// 如果当前ModelAttribute配置binding为false，表示不希望该属性绑定到model
				if (!ann.binding()) {
					container.setBindingDisabled(ann.name());
				}
				continue;
			}

			// 调用方法获取model属性的值
			Object returnValue = modelMethod.invokeForRequest(request, container);
			if (!modelMethod.isVoid()){
				// 获取属性名，先尝试获取ModelAttribute注解的value，如果没有，则使用被调用方法的返回值类型的类名，如String类型的则为string
				String returnValueName = getNameForReturnValue(returnValue, modelMethod.getReturnType());
				if (!ann.binding()) {
					container.setBindingDisabled(returnValueName);
				}
				if (!container.containsAttribute(returnValueName)) {
					container.addAttribute(returnValueName, returnValue);
				}
			}
		}
	}

	private ModelMethod getNextModelMethod(ModelAndViewContainer container) {
		for (ModelMethod modelMethod : this.modelMethods) {
			if (modelMethod.checkDependencies(container)) {
				if (logger.isTraceEnabled()) {
					logger.trace("Selected @ModelAttribute method " + modelMethod);
				}
				this.modelMethods.remove(modelMethod);
				return modelMethod;
			}
		}
		ModelMethod modelMethod = this.modelMethods.get(0);
		if (logger.isTraceEnabled()) {
			logger.trace("Selected @ModelAttribute method (not present: " +
					modelMethod.getUnresolvedDependencies(container)+ ") " + modelMethod);
		}
		this.modelMethods.remove(modelMethod);
		return modelMethod;
	}
  
	// 返回同时存在于SessionAttributes注解和ModelAttribute注解的属性名称
	private List<String> findSessionAttributeArguments(HandlerMethod handlerMethod) {
		List<String> result = new ArrayList<>();
		for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
			if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
				String name = getNameForParameter(parameter);
				Class<?> paramType = parameter.getParameterType();
				if (this.sessionAttributesHandler.isHandlerSessionAttribute(name, paramType)) {
					result.add(name);
				}
			}
		}
		return result;
	}
  
	public void updateModel(NativeWebRequest request, ModelAndViewContainer container) throws Exception {
		ModelMap defaultModel = container.getDefaultModel();
		if (container.getSessionStatus().isComplete()){
			this.sessionAttributesHandler.cleanupAttributes(request);
		}
		else {
			this.sessionAttributesHandler.storeAttributes(request, defaultModel);
		}
		if (!container.isRequestHandled() && container.getModel() == defaultModel) {
			updateBindingResult(request, defaultModel);
		}
	}
  
	private void updateBindingResult(NativeWebRequest request, ModelMap model) throws Exception {
		List<String> keyNames = new ArrayList<>(model.keySet());
		for (String name : keyNames) {
			Object value = model.get(name);
			if (value != null && isBindingCandidate(name, value)) {
				String bindingResultKey = BindingResult.MODEL_KEY_PREFIX + name;
				if (!model.containsAttribute(bindingResultKey)) {
					WebDataBinder dataBinder = this.dataBinderFactory.createBinder(request, value, name);
					model.put(bindingResultKey, dataBinder.getBindingResult());
				}
			}
		}
	}
  
	private boolean isBindingCandidate(String attributeName, Object value) {
		if (attributeName.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
			return false;
		}

		if (this.sessionAttributesHandler.isHandlerSessionAttribute(attributeName, value.getClass())) {
			return true;
		}

		return (!value.getClass().isArray() && !(value instanceof Collection) &&
				!(value instanceof Map) && !BeanUtils.isSimpleValueType(value.getClass()));
	}
  
	public static String getNameForParameter(MethodParameter parameter) {
		ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
		String name = (ann != null ? ann.value() : null);
		return (StringUtils.hasText(name) ? name : Conventions.getVariableNameForParameter(parameter));
	}
  
	public static String getNameForReturnValue(@Nullable Object returnValue, MethodParameter returnType) {
		ModelAttribute ann = returnType.getMethodAnnotation(ModelAttribute.class);
		if (ann != null && StringUtils.hasText(ann.value())) {
			return ann.value();
		}
		else {
			Method method = returnType.getMethod();
			Assert.state(method != null, "No handler method");
			Class<?> containingClass = returnType.getContainingClass();
			Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
			return Conventions.getVariableNameForReturnType(method, resolvedType, returnValue);
		}
	}


	private static class ModelMethod {

		private final InvocableHandlerMethod handlerMethod;

		private final Set<String> dependencies = new HashSet<>();

		public ModelMethod(InvocableHandlerMethod handlerMethod) {
			this.handlerMethod = handlerMethod;
			for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
				if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
					this.dependencies.add(getNameForParameter(parameter));
				}
			}
		}

		public InvocableHandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public boolean checkDependencies(ModelAndViewContainer mavContainer) {
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					return false;
				}
			}
			return true;
		}

		public List<String> getUnresolvedDependencies(ModelAndViewContainer mavContainer) {
			List<String> result = new ArrayList<>(this.dependencies.size());
			for (String name : this.dependencies) {
				if (!mavContainer.containsAttribute(name)) {
					result.add(name);
				}
			}
			return result;
		}

		@Override
		public String toString() {
			return this.handlerMethod.getMethod().toGenericString();
		}
	}

}
```

