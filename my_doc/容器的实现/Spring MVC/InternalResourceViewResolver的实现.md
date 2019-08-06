[InternalResourceViewResolver]用于根据视图名称获取视图，继承结构：
![InternalResourceViewResolver继承结构](../../img/InternalResourceViewResolver.png)

[ApplicationObjectSupport]和[WebApplicationObjectSupport]已经在笔记[SimpleUrlHandlerMapping的实现](SimpleUrlHandlerMapping的实现.md)介绍了，[ViewResolver]接口定义了获取视图的方法，代码：
```java
public interface ViewResolver {
	@Nullable
	View resolveViewName(String viewName, Locale locale) throws Exception;
}
```

[AbstractCachingViewResolver]提供了缓存的实现，具体获取视图的过程由子类实现，代码：
```java
public abstract class AbstractCachingViewResolver extends WebApplicationObjectSupport implements ViewResolver {

	/** Default maximum number of entries for the view cache: 1024 */
	public static final int DEFAULT_CACHE_LIMIT = 1024;

	/** Dummy marker object for unresolved views in the cache Maps */
	// 表示一个无法解析的视图
	private static final View UNRESOLVED_VIEW = new View() {
		@Override
		@Nullable
		public String getContentType() {
			return null;
		}
		@Override
		public void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
		}
	};


	/** The maximum number of entries in the cache */
	// 缓存大小限制
	private volatile int cacheLimit = DEFAULT_CACHE_LIMIT;

	/** Whether we should refrain from resolving views again if unresolved once */
	// 无法解析的视图是否也要缓存
	private boolean cacheUnresolved = true;

	/** Fast access cache for Views, returning already cached instances without a global lock */
	private final Map<Object, View> viewAccessCache = new ConcurrentHashMap<>(DEFAULT_CACHE_LIMIT);

	/** Map from view key to View instance, synchronized for View creation */
	@SuppressWarnings("serial")
	// 利用LinkedHashMap实现缓存
	private final Map<Object, View> viewCreationCache =
			new LinkedHashMap<Object, View>(DEFAULT_CACHE_LIMIT, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Object, View> eldest) {
					if (size() > getCacheLimit()) {
						viewAccessCache.remove(eldest.getKey());
						return true;
					}
					else {
						return false;
					}
				}
			};

	public void setCacheLimit(int cacheLimit) {
		this.cacheLimit = cacheLimit;
	}
  
	public int getCacheLimit() {
		return this.cacheLimit;
	}
  
	public void setCache(boolean cache) {
		this.cacheLimit = (cache ? DEFAULT_CACHE_LIMIT : 0);
	}

	/**
	 * Return if caching is enabled.
	 */
	public boolean isCache() {
		return (this.cacheLimit > 0);
	}
  
	public void setCacheUnresolved(boolean cacheUnresolved) {
		this.cacheUnresolved = cacheUnresolved;
	}
  
	public boolean isCacheUnresolved() {
		return this.cacheUnresolved;
	}


	@Override
	@Nullable
	public View resolveViewName(String viewName, Locale locale) throws Exception {
		// 如果没有开启缓存
		if (!isCache()) {
			// 创建视图，供子类实现
			return createView(viewName, locale);
		}
		else {
			Object cacheKey = getCacheKey(viewName, locale);
			View view = this.viewAccessCache.get(cacheKey);
			if (view == null) {
				synchronized (this.viewCreationCache) {
					view = this.viewCreationCache.get(cacheKey);
					// double check
					if (view == null) {
						// Ask the subclass to create the View object.
						// 创建视图，供子类实现
						view = createView(viewName, locale);
						if (view == null && this.cacheUnresolved) {
							view = UNRESOLVED_VIEW;
						}
						if (view != null) {
							this.viewAccessCache.put(cacheKey, view);
							this.viewCreationCache.put(cacheKey, view);
							if (logger.isTraceEnabled()) {
								logger.trace("Cached view [" + cacheKey + "]");
							}
						}
					}
				}
			}
			return (view != UNRESOLVED_VIEW ? view : null);
		}
	}
  
	protected Object getCacheKey(String viewName, Locale locale) {
		return viewName + '_' + locale;
	}
  
	public void removeFromCache(String viewName, Locale locale) {
		if (!isCache()) {
			logger.warn("View caching is SWITCHED OFF -- removal not necessary");
		}
		else {
			Object cacheKey = getCacheKey(viewName, locale);
			Object cachedView;
			synchronized (this.viewCreationCache) {
				this.viewAccessCache.remove(cacheKey);
				cachedView = this.viewCreationCache.remove(cacheKey);
			}
			if (logger.isDebugEnabled()) {
				// Some debug output might be useful...
				if (cachedView == null) {
					logger.debug("No cached instance for view '" + cacheKey + "' was found");
				}
				else {
					logger.debug("Cache for view " + cacheKey + " has been cleared");
				}
			}
		}
	}
  
	public void clearCache() {
		logger.debug("Clearing entire view cache");
		synchronized (this.viewCreationCache) {
			this.viewAccessCache.clear();
			this.viewCreationCache.clear();
		}
	}
  
	@Nullable
	protected View createView(String viewName, Locale locale) throws Exception {
		return loadView(viewName, locale);
	}
  
	@Nullable
	protected abstract View loadView(String viewName, Locale locale) throws Exception;

}
```

[UrlBasedViewResolver]实现了创建视图的逻辑，视图类型由`viewClass`属性指定，[UrlBasedViewResolver]还支持重定向和转发，当发送重定向和转发时将分别创建视图[RedirectView]和[InternalResourceView]，代码：
```java
public class UrlBasedViewResolver extends AbstractCachingViewResolver implements Ordered {
	// 重定向的前缀
	public static final String REDIRECT_URL_PREFIX = "redirect:";

	// 转发的前缀
	public static final String FORWARD_URL_PREFIX = "forward:";


	@Nullable
	// 视图的类型
	private Class<?> viewClass;

	private String prefix = "";

	private String suffix = "";

	@Nullable
	private String contentType;

	// 当重定向的路径是/开头时，是否认为是路径相对于当前ServletContext
	private boolean redirectContextRelative = true;

	// 重定向是否需要兼容http 1.0
	private boolean redirectHttp10Compatible = true;

	@Nullable
	// 表示能被重定向的域名，可以为空
	private String[] redirectHosts;

	@Nullable
	private String requestContextAttribute;

	/** Map of static attributes, keyed by attribute name (String) */
	// 静态属性，将会被添加到创建的视图属性中
	private final Map<String, Object> staticAttributes = new HashMap<>();

	@Nullable
	private Boolean exposePathVariables;

	@Nullable
	private Boolean exposeContextBeansAsAttributes;

	@Nullable
	private String[] exposedContextBeanNames;

	@Nullable
	private String[] viewNames;

	private int order = Ordered.LOWEST_PRECEDENCE;

	public void setViewClass(@Nullable Class<?> viewClass) {
		if (viewClass != null && !requiredViewClass().isAssignableFrom(viewClass)) {
			throw new IllegalArgumentException("Given view class [" + viewClass.getName() +
					"] is not of type [" + requiredViewClass().getName() + "]");
		}
		this.viewClass = viewClass;
	}
  
	@Nullable
	protected Class<?> getViewClass() {
		return this.viewClass;
	}

	protected Class<?> requiredViewClass() {
		return AbstractUrlBasedView.class;
	}
  
	public void setPrefix(@Nullable String prefix) {
		this.prefix = (prefix != null ? prefix : "");
	}
  
	protected String getPrefix() {
		return this.prefix;
	}
  
	public void setSuffix(@Nullable String suffix) {
		this.suffix = (suffix != null ? suffix : "");
	}
  
	protected String getSuffix() {
		return this.suffix;
	}
  
	public void setContentType(@Nullable String contentType) {
		this.contentType = contentType;
	}
  
	@Nullable
	protected String getContentType() {
		return this.contentType;
	}
  
	public void setRedirectContextRelative(boolean redirectContextRelative) {
		this.redirectContextRelative = redirectContextRelative;
	}
  
	protected boolean isRedirectContextRelative() {
		return this.redirectContextRelative;
	}
  
	public void setRedirectHttp10Compatible(boolean redirectHttp10Compatible) {
		this.redirectHttp10Compatible = redirectHttp10Compatible;
	}
  
	protected boolean isRedirectHttp10Compatible() {
		return this.redirectHttp10Compatible;
	}
  
	public void setRedirectHosts(@Nullable String... redirectHosts) {
		this.redirectHosts = redirectHosts;
	}
  
	@Nullable
	public String[] getRedirectHosts() {
		return this.redirectHosts;
	}
  
	public void setRequestContextAttribute(@Nullable String requestContextAttribute) {
		this.requestContextAttribute = requestContextAttribute;
	}
  
	@Nullable
	protected String getRequestContextAttribute() {
		return this.requestContextAttribute;
	}
  
	public void setAttributes(Properties props) {
		CollectionUtils.mergePropertiesIntoMap(props, this.staticAttributes);
	}
  
	public void setAttributesMap(@Nullable Map<String, ?> attributes) {
		if (attributes != null) {
			this.staticAttributes.putAll(attributes);
		}
	}
  
	public Map<String, Object> getAttributesMap() {
		return this.staticAttributes;
	}
  
	public void setExposePathVariables(@Nullable Boolean exposePathVariables) {
		this.exposePathVariables = exposePathVariables;
	}
  
	@Nullable
	protected Boolean getExposePathVariables() {
		return this.exposePathVariables;
	}

	public void setExposeContextBeansAsAttributes(boolean exposeContextBeansAsAttributes) {
		this.exposeContextBeansAsAttributes = exposeContextBeansAsAttributes;
	}

	@Nullable
	protected Boolean getExposeContextBeansAsAttributes() {
		return this.exposeContextBeansAsAttributes;
	}
  
	public void setExposedContextBeanNames(@Nullable String... exposedContextBeanNames) {
		this.exposedContextBeanNames = exposedContextBeanNames;
	}

	@Nullable
	protected String[] getExposedContextBeanNames() {
		return this.exposedContextBeanNames;
	}
  
	public void setViewNames(@Nullable String... viewNames) {
		this.viewNames = viewNames;
	}
  
	@Nullable
	protected String[] getViewNames() {
		return this.viewNames;
	}
  
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	protected void initApplicationContext() {
		super.initApplicationContext();
		if (getViewClass() == null) {
			throw new IllegalArgumentException("Property 'viewClass' is required");
		}
	}
  
	@Override
	protected Object getCacheKey(String viewName, Locale locale) {
		return viewName;
	}
  
	@Override
	protected View createView(String viewName, Locale locale) throws Exception {
		// If this resolver is not supposed to handle the given view,
		// return null to pass on to the next resolver in the chain.
		// 如果viewNames属性不为空，则根据viewNames属性判断当前视图名称是否能被解析
		if (!canHandle(viewName, locale)) {
			return null;
		}
		// Check for special "redirect:" prefix.
		// 如果是重定向请求
		if (viewName.startsWith(REDIRECT_URL_PREFIX)) {
			String redirectUrl = viewName.substring(REDIRECT_URL_PREFIX.length());
			// 创建重定向视图
			RedirectView view = new RedirectView(redirectUrl, isRedirectContextRelative(), isRedirectHttp10Compatible());
			// 获取能够被重定向的域名，可以为空
			String[] hosts = getRedirectHosts();
			if (hosts != null) {
				view.setHosts(hosts);
			}
			// 将创建的视图作为bean添加到applicationContext
			return applyLifecycleMethods(viewName, view);
		}
		// Check for special "forward:" prefix.
		// 如果是转发请求则创建对应的视图，该视图没有被添加到applicationContext
		if (viewName.startsWith(FORWARD_URL_PREFIX)) {
			String forwardUrl = viewName.substring(FORWARD_URL_PREFIX.length());
			return new InternalResourceView(forwardUrl);
		}
		// Else fall back to superclass implementation: calling loadView.
		return super.createView(viewName, locale);
	}
  
	protected boolean canHandle(String viewName, Locale locale) {
		String[] viewNames = getViewNames();
		return (viewNames == null || PatternMatchUtils.simpleMatch(viewNames, viewName));
	}

	@Override
	protected View loadView(String viewName, Locale locale) throws Exception {
		AbstractUrlBasedView view = buildView(viewName);
		// 将视图作为bean添加到applicationContext中
		View result = applyLifecycleMethods(viewName, view);
		return (view.checkResource(locale) ? result : null);
	}
  
	protected AbstractUrlBasedView buildView(String viewName) throws Exception {
		Class<?> viewClass = getViewClass();
		Assert.state(viewClass != null, "No view class");

		// 实例化视图类
		AbstractUrlBasedView view = (AbstractUrlBasedView) BeanUtils.instantiateClass(viewClass);
		// 设置视图的路径
		view.setUrl(getPrefix() + viewName + getSuffix());

		String contentType = getContentType();
		if (contentType != null) {
			view.setContentType(contentType);
		}

		view.setRequestContextAttribute(getRequestContextAttribute());
		// 添加staticAttributes到视图属性
		view.setAttributesMap(getAttributesMap());

		Boolean exposePathVariables = getExposePathVariables();
		// 设置是否需要暴露请求路径中的属性
		if (exposePathVariables != null) {
			view.setExposePathVariables(exposePathVariables);
		}
		Boolean exposeContextBeansAsAttributes = getExposeContextBeansAsAttributes();
		// 设置是否需要暴露所有applicationContext的bean到视图属性
		if (exposeContextBeansAsAttributes != null) {
			view.setExposeContextBeansAsAttributes(exposeContextBeansAsAttributes);
		}
		String[] exposedContextBeanNames = getExposedContextBeanNames();
		// 设置允许暴露的bean到视图属性，如果为空并且exposeContextBeansAsAttributes为true则所有bean都会被暴露
		if (exposedContextBeanNames != null) {
			view.setExposedContextBeanNames(exposedContextBeanNames);
		}

		return view;
	}
  
	protected View applyLifecycleMethods(String viewName, AbstractUrlBasedView view) {
		ApplicationContext context = getApplicationContext();
		if (context != null) {
			Object initialized = context.getAutowireCapableBeanFactory().initializeBean(view, viewName);
			if (initialized instanceof View) {
				return (View) initialized;
			}
		}
		return view;
	}

}
```

[InternalResourceViewResolver]指定了默认视图的类型，并在创建视图后为视图设置了若干属性，代码：
```java
public class InternalResourceViewResolver extends UrlBasedViewResolver {

	private static final boolean jstlPresent = ClassUtils.isPresent(
			"javax.servlet.jsp.jstl.core.Config", InternalResourceViewResolver.class.getClassLoader());

	@Nullable
	// 要求返回的结果使用include方式而不是forward方式
  private Boolean alwaysInclude;

	public InternalResourceViewResolver() {
		// 获取视图类型，默认InternalResourceView
		Class<?> viewClass = requiredViewClass();
		if (InternalResourceView.class == viewClass && jstlPresent) {
			viewClass = JstlView.class;
		}
		setViewClass(viewClass);
	}
  
	public InternalResourceViewResolver(String prefix, String suffix) {
		this();
		setPrefix(prefix);
		setSuffix(suffix);
	}
  
	@Override
	protected Class<?> requiredViewClass() {
		return InternalResourceView.class;
	}
  
	public void setAlwaysInclude(boolean alwaysInclude) {
		this.alwaysInclude = alwaysInclude;
	}

	@Override
	protected AbstractUrlBasedView buildView(String viewName) throws Exception {
		InternalResourceView view = (InternalResourceView) super.buildView(viewName);
		if (this.alwaysInclude != null) {
			view.setAlwaysInclude(this.alwaysInclude);
		}
		view.setPreventDispatchLoop(true);
		return view;
	}

}
```

[InternalResourceViewResolver]的作用主要是创建视图，而默认情况下视图的实现是[InternalResourceView]，这里再看一下[InternalResourceView]的实现，继承结构：
![InternalResourceView继承结构](../../img/InternalResourceView.png)



[ApplicationObjectSupport]: aaa
[WebApplicationObjectSupport]: aaa
[ViewResolver]: aaa
[AbstractCachingViewResolver]: aaa
[UrlBasedViewResolver]: aaa
[InternalResourceViewResolver]: aaa
[InternalResourceView]: aaa