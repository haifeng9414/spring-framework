## 从容器获取Bean

在初始化完容器后，容器的[BeanFactory]也就初始化完了，容器中Bean都是从[BeanFactory]中获取的，同样以[ClassPathXmlApplicationContext]为例，[ClassPathXmlApplicationContext]的[BeanFactory]实现是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]继承结构图如下：
![DefaultListableBeanFactory继承结构图](../../img/DefaultListableBeanFactory.png)

[DefaultListableBeanFactory]实现了很多接口，每个接口都赋予了[DefaultListableBeanFactory]不同的职能：
1. [AliasRegistry]接口包含了别名管理的相关方法
2. [SimpleAliasRegistry]类利用Map实现了[AliasRegistry]接口并提供了循环别名检查
3. [BeanDefinitionRegistry]接口添加了[BeanDefinition]的注册方法，能将`beanName`关联到[BeanDefinition]，[BeanFactory]接口定义了访问Spring容器的相关操作，如`getBean()`，`isSingleton()`等方法，接口上的注释说明了Bean的生命周期里涉及到的接口和对应的方法:
   1. BeanNameAware's setBeanName
   2. BeanClassLoaderAware's setBeanClassLoader
   3. BeanFactoryAware's setBeanFactory
   4. EnvironmentAware's setEnvironment
   5. EmbeddedValueResolverAware's setEmbeddedValueResolver
   6. ResourceLoaderAware's setResourceLoader (only applicable when running in an application context)
   7.  ApplicationEventPublisherAware's setApplicationEventPublisher (only applicable when running in an application context)
   8.  MessageSourceAware's setMessageSource (only applicable when running in an application context)
   9.  ApplicationContextAware's setApplicationContext (only applicable when running in an application context)
   10. ServletContextAware's setServletContext (only applicable when running in a web application context)
   11. postProcessBeforeInitialization methods of BeanPostProcessors
   12. InitializingBean's afterPropertiesSet
   13. a custom init-method definition
   14. postProcessAfterInitialization methods of BeanPostProcessors

    [BeanFactory]销毁时对应的接口和对应的方法
    
   15. postProcessBeforeDestruction methods of DestructionAwareBeanPostProcessors
   16. DisposableBean's destroy
   17. a custom destroy-method definition

4. [SingletonBeanRegistry]接口定义了单例[BeanFactory]的相关方法如`registerSingleton(String beanName, Object singletonObject)`、`getSingleton()`
5. [DefaultSingletonBeanRegistry]类实现了基本的单例的注册功能，相当于是一个单例bean的[BeanFactory]，并为单例bean的循环引用提供了支持
6. [FactoryBeanRegistrySupport]类增加了对[FactoryBean]的支持，能够从[FactoryBean]中获取Bean(主要方法是`getObjectFromFactoryBean`)
7. [HierarchicalBeanFactory]接口在[BeanFactory]基础上添加了两个方法`getParentBeanFactory`和`containsLocalBean`，增加了[BeanFactory]的继承功能
8. [ConfigurableBeanFactory]添加了配置[BeanFactory]的方法，该接口主要在Spring内部配置[BeanFactory]时使用，如设置[BeanExpressionResolver]、[ConversionService]、[PropertyEditorRegistrar]等
9. [AbstractBeanFactory]类实现了[ConfigurableBeanFactory]接口的全部方法并继承了[FactoryBeanRegistrySupport]类，用模版方法模式定义好了基本逻辑并提供一些抽象方法供子类实现(主要是`getBeanDefinition`和`createBean`)
10. [AutowireCapableBeanFactory]接口增加了创建Bean、自动注入、初始化以及[BeanPostProcessor]等相关方法
11. [AbstractAutowireCapableBeanFactory]类继承[AbstractBeanFactory]并实现了[AutowireCapableBeanFactory]接口
12. [ListableBeanFactory]接口定义了获取Bean配置清单的相关方法
13. [ConfigurableListableBeanFactory]接口添加了分析和修改[BeanDefinition]的方法
14. [DefaultListableBeanFactory]综合上面所有功能，主要是对bean注册后的处理

上面相关接口和抽象类的具体实现可以看代码中的注释，下面着重介绍Bean的获取过程，Bean的获取入口是`getBean()`方法，该方法有多个重载版本，典型的是`getBeanFactory().getBean(name, requiredType)`方法，该方法的实现为[AbstractBeanFactory]类的`doGetBean(final String name, @Nullable final Class<T> requiredType, @Nullable final Object[] args, boolean typeCheckOnly)`方法

[ClassPathXmlApplicationContext]: aaa
[XmlBeanDefinitionReader]: aaa
[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
[AliasRegistry]: aaa
[SimpleAliasRegistry]: aaa
[BeanDefinitionRegistry]: aaa
[BeanDefinition]: aaa
[BeanFactory]: aaa
[SingletonBeanRegistry]: aaa
[DefaultSingletonBeanRegistry]: aaa
[FactoryBeanRegistrySupport]: aaa
[FactoryBean]: aaa
[HierarchicalBeanFactory]: aaa
[ConfigurableBeanFactory]: aaa
[AbstractBeanFactory]: aaa
[AutowireCapableBeanFactory]: aaa
[AbstractAutowireCapableBeanFactory]: aaa
[ListableBeanFactory]: aaa
[ConfigurableListableBeanFactory]: aaa