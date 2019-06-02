## 从容器获取Bean

在初始化完容器后，[BeanFactory]也就初始化完了，容器中Bean都是从[BeanFactory]中获取的，同样以[ClassPathXmlApplicationContext]为例，[ClassPathXmlApplicationContext]的[BeanFactory]实现是[DefaultListableBeanFactory]，[DefaultListableBeanFactory]继承结构图如下：
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
public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
	return doGetBean(name, requiredType, null, false);
}

// 该方法实现了从容器获取Bean的基本过程，用模版方法模式调用了几个抽象方法实现创建Bean的过程
protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
        @Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {

    // 提取bean的名字，如果是别名则返回的是真正的bean的名字，如果是FactoryBean的名字则去掉前面的BeanFactory.FACTORY_BEAN_PREFIX
    final String beanName = transformedBeanName(name);
    Object bean;

    // Eagerly check singleton cache for manually registered singletons.
    // 从缓存中获取单例bean，如果存在则返回
    Object sharedInstance = getSingleton(beanName);
    if (sharedInstance != null && args == null) {
        if (logger.isDebugEnabled()) {
            if (isSingletonCurrentlyInCreation(beanName)) {
                logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
                        "' that is not fully initialized yet - a consequence of a circular reference");
            }
            else {
                logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
            }
        }
        // 如果sharedInstance是一个普通bean的话直接返回，如果是FactoryBean的话返回getObject方法的返回值
        bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        // Fail if we're already creating this bean instance:
        // We're assumably within a circular reference.
        // 对于以prototype为scope的bean，如果在当前加载过程中发现当前bean已经处于创建过程，则抛出异常，防止循环引用
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // Check if bean definition exists in this factory.
        // 判断是否存在父BeanFactory，如果存在并且当前BeanFactory的BeanDefinition中不包含正在创建的bean对应的BeanDefinition则从父BeanFactory获取bean
        BeanFactory parentBeanFactory = getParentBeanFactory();
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // Not found -> check parent.
            // 还原beanName，如果是FactoryBean则为&beanName
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                        nameToLookup, requiredType, args, typeCheckOnly);
            }
            else if (args != null) {
                // Delegation to parent with explicit args.
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
        }

        // 如果不仅仅是类型检查则将当前bean置成已创建状态，即保存到alreadyCreated中
        if (!typeCheckOnly) {
            markBeanAsCreated(beanName);
        }

        try {
            // 获取bean的BeanDefinition
            final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            // 如果BeanDefinition声明了bean是abstract的则报错
            checkMergedBeanDefinition(mbd, beanName, args);

            // Guarantee initialization of beans that the current bean depends on.
            // 确保当前bean声明的dependOn的bean已经被初始化了，这里的dependOn不同于bean的属性依赖，而是用户自己声明的
            // 如BeanA可以声明dependOn BeanB，即使两个bean没有关系，dependOn用于确保创建bean之前已经创建了某些bean，
            // 并且dependOn不可以循环依赖
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                // 如果存在dependsOn则遍历并创建
                for (String dep : dependsOn) {
                    // 判断dep是否依赖beanName，如果是则为循环依赖，报错
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    // 保存beanName依赖dep的依赖关系
                    registerDependentBean(dep, beanName);
                    try {
                        // 创建bean，如果不存在则抛出异常
                        getBean(dep);
                    }
                    catch (NoSuchBeanDefinitionException ex) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                "'" + beanName + "' depends on missing bean '" + dep + "'", ex);
                    }
                }
            }

            // Create bean instance.
            // 如果bean是单例的
            if (mbd.isSingleton()) {
                // 单例bean是支持循环引用的，处理过程是，这里先创建一个ObjectFactory（下面的lambda就是ObjectFactory实例），在getSingleton中将会调用这里的匿名ObjectFactory的
                // createBean方法，而createBean方法会调用doCreateBean方法创建bean，doCreateBean在创建bean时解析构造函数或者工厂方法
                // 创建一个没有注入任何属性的简单bean，在创建完成后执行addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
                // 将能够返回刚创建的bean的匿名ObjectFactory添加到内存中，getEarlyBeanReference遍历所有的SmartInstantiationAwareBeanPostProcessor对bean做一些特殊处理，AOP就是通过
                // SmartInstantiationAwareBeanPostProcessor实现的，如果不存在SmartInstantiationAwareBeanPostProcessor则直接返回简单bean。在将ObjectFactory添加到内存中后将继续该bean的创建，
                // 首先是populateBean方法，该方法将填充bean的属性，而填充属性就可能导致循环引用，假设当前正在创建的bean是beanA，而beanA有属性beanB，populateBean就将填充beanB到beanA，
                // 由于之前没有创建过beanB，所以填充之前将会创建一个beanB，如果beanB也有属性beanA，则存在循环引用，在创建beanB的时候将会尝试获取beanA，获取方式就是执行getBean(beanA)，调用getBean(beanA)使得
                // getSingleton(beanA)方法被调用，该方法将会获取到之前添加到内存的匿名beanFactory并通过该beanFactory的getBean方法获取到刚创建完成正在
                // 填充属性的beanA，在获取到beanA后beanB就能够顺利初始化了，之后返回beanB并继续beanA的创建，以上就是解决循环引用的过程
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        /*
                        该方法创建bean，创建过程中涉及到了多个接口方法调用，顺序和用途如下:
                        1.如果当前BeanFactory存在InstantiationAwareBeanPostProcessor则调用该InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法，
                        如果方法返回了一个对象则以该对象作为bean返回并在返回前遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法(该方法的设计目的是在bean及其属性初始化完毕后调用，
                        由于InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation返回的对象将作为最终的bean，所以需要在返回前调用该方法)对bean进行处理，如果遍历过程中某个
                        BeanPostProcessor的postProcessAfterInitialization返回null则结束遍历，否则以返回的对象为bean并继续遍历
                        2.如果正在创建的bean有Supplier则以Supplier提供的对象为bean，否则如果存在工厂方法则以工厂方法的返回结果做为bean，否则如果存在构造函数参数则根据构造函数参数，自动解析所有构造函数，
                        或在SmartInstantiationAwareBeanPostProcessor接口的返回的构造函数列表中，获取满足构造函数参数的构造函数并创建bean，否则以默认构造函数创建bean
                        3.获取所有的MergedBeanDefinitionPostProcessor调用postProcessMergedBeanDefinition对正在创建的bean的BeanDefinition就行处理
                        4.根据创建出来的bean创建ObjectFactory并将ObjectFactory添加到singletonFactories以支持循环依赖
                        5.开始设置bean的属性，在此之前再次获取所有的InstantiationAwareBeanPostProcessor，执行postProcessAfterInstantiation方法，目的是在设置属性之前对bean做定制，
                        遍历过程中如果某个InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation返回false则结束遍历
                        6.根据autowire的设置，如果是byType或byName则用相应的策略填充属性，对于循环依赖的属性解决方法在上面的注释已经说了
                        7.如果bean实现了BeanNameAware、BeanClassLoaderAware或者BeanFactoryAware则调用相应的接口方法
                        8.遍历所有的BeanPostProcessor调用postProcessBeforeInitialization方法，此时bean的依赖都注入了
                        9.如果bean实现了InitializingBean接口则调用afterPropertiesSet方法，如果bean存在init-method则调用自定义的方法
                        10.遍历所有的BeanPostProcessor调用postProcessAfterInitialization方法
                        11.注册当前bean的DisposableBeanAdapter对象用于在销毁bean时遍历DestructionAwareBeanPostProcessor调用回调方法，如果正在销毁的bean实现了DisposableBean接口
                        则调用destroy方法
                         */

                        return createBean(beanName, mbd, args);
                    }
                    catch (BeansException ex) {
                        // Explicitly remove instance from singleton cache: It might have been put there
                        // eagerly by the creation process, to allow for circular reference resolution.
                        // Also remove any beans that received a temporary reference to the bean.
                        destroySingleton(beanName);
                        throw ex;
                    }
                });
                // 如果是FactoryBean则调用getObject方法获取bean
                bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            else if (mbd.isPrototype()) {
                // It's a prototype -> create a new instance.
                Object prototypeInstance = null;
                try {
                    // 将beanName添加到prototypesCurrentlyInCreation中，在doGetBean方法每次创建bean的时候判断prototypesCurrentlyInCreation
                    // 是否存在当前正在创建的bean，如果存在则说明有循环依赖，直接报错
                    beforePrototypeCreation(beanName);
                    // 非单例bean每次都创建一个新的，所以不需要用ObjectFactory了
                    prototypeInstance = createBean(beanName, mbd, args);
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                // 如果是FactoryBean则调用getObject方法获取bean
                bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            //如果不是原型的也不是单例的则创建scope的bean
            else {
                String scopeName = mbd.getScope();
                final Scope scope = this.scopes.get(scopeName);
                if (scope == null) {
                    throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
                }
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args);
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName,
                            "Scope '" + scopeName + "' is not active for the current thread; consider " +
                            "defining a scoped proxy for this bean if you intend to refer to it from a singleton",
                            ex);
                }
            }
        }
        catch (BeansException ex) {
            cleanupAfterBeanCreationFailure(beanName);
            throw ex;
        }
    }

    // 转换bean为需要的类型
    // Check if required type matches the type of the actual bean instance.
    if (requiredType != null && !requiredType.isInstance(bean)) {
        try {
            T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
            if (convertedBean == null) {
                throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
            }
            return convertedBean;
        }
        catch (TypeMismatchException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to convert bean '" + name + "' to required type '" +
                        ClassUtils.getQualifiedName(requiredType) + "'", ex);
            }
            throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
        }
    }
    return (T) bean;
}
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