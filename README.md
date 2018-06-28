# 个人总结:

- [容器的实现]
    - 加载Bean配置信息(#加载Bean配置信息)
    - 从容器获取bean

## 加载Bean配置信息

1. 加载Bean的配置信息有很多中方法，如从XML解析、根据注解解析等，下面主要介绍根据XML文件解析Bean配置信息。
Spring中从XML文件解析Bean配置的信息的默认实现是[XmlBeanDefinitionReader]，该类的构造函数接收[BeanDefinitionRegistry]类为参数，
[BeanDefinitionRegistry]用于Bean信息的增删改查，默认实现是[DefaultListableBeanFactory]，最简单的Spring容器测试代码如下:
```java
DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
new XmlBeanDefinitionReader(factory).loadBeanDefinitions(PATH_TO_XML);
```
[DefaultListableBeanFactory]的继承结构如下:
![DefaultListableBeanFactory继承结构图](img/DefaultListableBeanFactory.png)
[DefaultListableBeanFactory]实现了很多接口，每个接口都赋予了[DefaultListableBeanFactory]不同的职能，[AliasRegistry]接口包含了别名管理的相关方法，
[SimpleAliasRegistry]类利用Map实现了[AliasRegistry]接口并提供了循环别名检查，[BeanDefinitionRegistry]接口添加了[BeanDefinition]的注册方法，能将`beanName关联到[BeanDefinition]，
[BeanFactory]接口定义了访问Spring容器的相关操作，如`getBean()`，`isSingleton()`等方法，接口上的注释说明了bean的生命周期里涉及到的接口和对应的方法:
1. BeanNameAware's setBeanName
1. BeanClassLoaderAware's setBeanClassLoader
1. BeanFactoryAware's setBeanFactory
1. EnvironmentAware's setEnvironment
1. EmbeddedValueResolverAware's setEmbeddedValueResolver
1. ResourceLoaderAware's setResourceLoader (only applicable when running in an application context)
1. ApplicationEventPublisherAware's setApplicationEventPublisher (only applicable when running in an application context)
1. MessageSourceAware's setMessageSource (only applicable when running in an application context)
1. ApplicationContextAware's setApplicationContext (only applicable when running in an application context)
1. ServletContextAware's setServletContext (only applicable when running in a web application context)
1. postProcessBeforeInitialization methods of BeanPostProcessors
1. InitializingBean's afterPropertiesSet
1. a custom init-method definition
1. postProcessAfterInitialization methods of BeanPostProcessors

`beanFactory`销毁时对应的接口和对应的方法
1. postProcessBeforeDestruction methods of DestructionAwareBeanPostProcessors
1. DisposableBean's destroy
1. a custom destroy-method definition

[XmlBeanDefinitionReader]: aaa
[BeanDefinitionRegistry]: aaa
[DefaultListableBeanFactory]: aaa
[AliasRegistry]: aaa
[SimpleAliasRegistry]: aaa
[BeanDefinitionRegistry]: aaa
[BeanFactory]: aaa

## 从容器获取bean

1. 