## 从容器获取Bean

在初始化完容器后，[BeanFactory]也就初始化完了，容器中Bean都是从[BeanFactory]中获取的，同样以[ClassPathXmlApplicationContext]为例，[ClassPathXmlApplicationContext]的[BeanFactory]实现是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]继承结构图如下：
![DefaultListableBeanFactory继承结构图](../../img/DefaultListableBeanFactory.png)

获取Bean：
```java
ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext(classPathResource("-application-context.xml").getPath(), getClass());
		MyBeanA myBeanA = applicationContext.getBean("myBeanA", MyBeanA.class);
```
getBean方法代码：
```java
public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}
```

容器获取Bean的过程完全委托给了[BeanFactory]，默认实现就是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]实现了很多接口，每个接口都赋予了[DefaultListableBeanFactory]不同的职能：
1. [AliasRegistry]接口包含了别名管理的相关方法
2. [SimpleAliasRegistry]类利用Map实现了[AliasRegistry]接口并提供了别名循环别名检查
3. [BeanDefinitionRegistry]接口定义了[BeanDefinition]的管理方法，包括`beanName`与[BeanDefinition]关联关系的增删改查
4. [BeanFactory]接口定义了访问Bean的相关操作，如`getBean()`，`isSingleton()`，`getType()`等方法
5. [SingletonBeanRegistry]接口定义了访问和注册单例Bean的相关方法，如`registerSingleton()`，`getSingleton()`
6. [DefaultSingletonBeanRegistry]类实现了基本的单例的注册功能，相当于是一个单例Bean的[BeanFactory]，并为单例Bean的循环引用提供了支持，该类的方法在获取单例Bean的过程中起了决定性作用，下面会具体分析
7. [FactoryBeanRegistrySupport]类增加了对[FactoryBean]的支持，能够从[FactoryBean]中获取Bean(主要方法是`getObjectFromFactoryBean`)
8. [HierarchicalBeanFactory]接口在[BeanFactory]基础上添加了两个方法`getParentBeanFactory`和`containsLocalBean`，增加了[BeanFactory]的继承功能
9. [ConfigurableBeanFactory]添加了配置[BeanFactory]的方法，即若干个setter方法，该接口主要在Spring内部配置[BeanFactory]时使用，如设置[BeanFactory]的[BeanExpressionResolver]、[ConversionService]、[PropertyEditorRegistrar]等
10. [AbstractBeanFactory]类实现了[ConfigurableBeanFactory]接口的全部方法并继承了[FactoryBeanRegistrySupport]类，用模版方法模式定义好了获取bean的基本逻辑，提供一些抽象方法供子类实现(主要是`getBeanDefinition`和`createBean`)，从容器获取Bean的主要过程就在该类的`doGetBean`方法
11. [AutowireCapableBeanFactory]接口增加了创建Bean、自动注入、初始化以及调用[BeanPostProcessor]等相关方法
12. [AbstractAutowireCapableBeanFactory]类继承[AbstractBeanFactory]并实现了[AutowireCapableBeanFactory]接口，bean的属性和构造函数参数自动注入都是该类实现的
13. [ListableBeanFactory]接口定义了获取Bean配置清单的相关方法，如获取所有的BeanName，根据类型、注解获取Bean列表等
14. [ConfigurableListableBeanFactory]接口添加了分析和修改[BeanDefinition]、单例预实例化等方法，该接口主要在Spring内部使用
15. [DefaultListableBeanFactory]综合上面所有功能，并实现了[ListableBeanFactory]和[BeanDefinitionRegistry]接口

上面相关接口和抽象类的具体实现可以看代码中的注释，下面着重介绍Bean的获取过程，Bean的获取入口是[DefaultListableBeanFactory]的`getBean()`方法，代码：
```java
```

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