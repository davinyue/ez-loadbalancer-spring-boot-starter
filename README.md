# EZ LoadBalancer Spring Boot Starter

## 简介

`ez-loadbalancer-spring-boot-starter` 是一个基于 Spring Cloud LoadBalancer 的区域和机房感知的负载均衡 Starter。它提供了智能的服务实例选择策略,支持静态配置和动态请求级别的区域机房指定,提高服务调用的性能和可靠性。

## 核心特性

- ✅ **区域和机房感知**: 自动识别服务实例的区域(region)和机房(zone)信息
- ✅ **智能实例过滤**: 按优先级选择最优服务实例
- ✅ **动态负载均衡**: 支持通过 HTTP 请求头动态指定区域和机房
- ✅ **可访问区域配置**: 支持通过 metadata 配置可访问的区域和机房列表
- ✅ **自动降级策略**: 当最优实例不可用时,自动降级到次优实例
- ✅ **零配置启用**: 引入依赖即可自动启用,无需额外配置
- ✅ **多注册中心支持**: 支持 Eureka、Nacos、Consul、Zookeeper 等注册中心
- ✅ **性能优化**: 使用缓存机制避免重复解析配置

## 实例选择优先级

1. **同区域同机房** (最优先,无需检查可访问列表)
2. **同区域可访问机房** (检查 `available-region-zones` 配置)
3. **同区域所有机房** (忽略访问限制,作为降级策略)
4. **其他区域** (兜底策略)

## 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加以下依赖:

```xml
<dependency>
    <groupId>org.rdlinux</groupId>
    <artifactId>ez-loadbalancer-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 2. 配置服务实例元数据

根据使用的注册中心,配置区域和机房信息:

#### 2.1 Eureka 配置

```yaml
spring:
  application:
    name: your-service-name

eureka:
  instance:
    metadata-map:
      region: A              # 区域标识
      zone: zone-1           # 机房标识
      # 可选: 配置可访问的区域和机房列表
      # available-region-zones: "A:zone-1,zone-2;B:zone-1"
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

#### 2.2 Nacos 配置

```yaml
spring:
  application:
    name: your-service-name
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        metadata:
          region: A              # 区域标识
          zone: zone-1           # 机房标识
          # 可选: 配置可访问的区域和机房列表
          # available-region-zones: "A:zone-1,zone-2;B:zone-1"
```

#### 2.3 Consul 配置

```yaml
spring:
  application:
    name: your-service-name
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
        metadata:
          region: A              # 区域标识
          zone: zone-1           # 机房标识
          # 可选: 配置可访问的区域和机房列表
          # available-region-zones: "A:zone-1,zone-2;B:zone-1"
```

#### 2.4 Zookeeper 配置

```yaml
spring:
  application:
    name: your-service-name
  cloud:
    zookeeper:
      connect-string: localhost:2181
      discovery:
        instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
        metadata:
          region: A              # 区域标识
          zone: zone-1           # 机房标识
          # 可选: 配置可访问的区域和机房列表
          # available-region-zones: "A:zone-1,zone-2;B:zone-1"
```

### 3. 启用服务发现和 Feign 客户端

在启动类上添加注解:

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 4. 创建 Feign 客户端

```java
@FeignClient(name = "target-service-name")
public interface TargetServiceClient {
    
    @GetMapping("/api/example")
    String getExample();
}
```

## 使用方式

### 静态配置方式

通过配置文件指定服务实例的区域和机房,负载均衡器会根据调用方的区域和机房自动选择最优实例。

```yaml
eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
```

### 动态请求方式

通过 HTTP 请求头动态指定区域和机房,实现请求级别的负载均衡:

```bash
curl -H "X-Region: B" -H "X-Zone: zone-2" http://your-service/api/endpoint
```

**请求头说明:**
- `X-Region`: 指定请求的目标区域
- `X-Zone`: 指定请求的目标机房

**优先级:**
1. 优先使用请求头中的 `X-Region` 和 `X-Zone`
2. 如果请求头中没有,则使用服务实例自身配置的 `region` 和 `zone`

## 可访问区域配置

服务实例可以通过 `available-region-zones` 配置限制哪些区域和机房可以访问:

### 配置格式

```
region1:zone1,zone2;region2:zone3,zone4
```

- 多个区域用分号 `;` 分隔
- 同一区域的多个机房用逗号 `,` 分隔

### 配置示例

#### 示例 1: 只允许特定机房访问

```yaml
eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
      # 只允许 A 区域的 zone-1 访问
      available-region-zones: "A:zone-1"
```

#### 示例 2: 允许多个机房访问

```yaml
eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
      # 允许 A 区域的 zone-1、zone-2 访问
      available-region-zones: "A:zone-1,zone-2"
```

#### 示例 3: 允许跨区域访问

```yaml
eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
      # 允许 A 区域的 zone-1、zone-2 和 B 区域的 zone-1 访问
      available-region-zones: "A:zone-1,zone-2;B:zone-1"
```

#### 示例 4: 不配置(默认行为)

```yaml
eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
      # 不配置 available-region-zones,默认允许同区域所有机房访问
```

## 完整使用示例

### 场景: 多区域多机房部署

假设有以下部署架构:
- **区域 A**: zone-1, zone-2, zone-3
- **区域 B**: zone-1, zone-2

### 服务 A (调用方)

**application.yml:**
```yaml
spring:
  application:
    name: service-a

eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

**FeignClient:**
```java
@FeignClient(name = "service-b")
public interface ServiceBClient {
    @GetMapping("/api/hello")
    String hello();
}
```

### 服务 B (被调用方)

#### 实例 1 (A/zone-1)

```yaml
spring:
  application:
    name: service-b
server:
  port: 8081

eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-1
      # 只允许 A 区域的 zone-1 访问
      available-region-zones: "A:zone-1"
```

#### 实例 2 (A/zone-2)

```yaml
spring:
  application:
    name: service-b
server:
  port: 8082

eureka:
  instance:
    metadata-map:
      region: A
      zone: zone-2
      # 允许 A 区域的 zone-1 和 zone-2 访问
      available-region-zones: "A:zone-1,zone-2"
```

#### 实例 3 (B/zone-1)

```yaml
spring:
  application:
    name: service-b
server:
  port: 8083

eureka:
  instance:
    metadata-map:
      region: B
      zone: zone-1
      # 不配置,默认允许同区域所有机房访问
```

### 负载均衡行为

当服务 A (region=A, zone=zone-1) 调用服务 B 时:

1. **优先选择**: 实例 1 (同区域同机房: A/zone-1)
2. **次优选择**: 实例 2 (同区域可访问机房: A/zone-2,且配置允许 zone-1 访问)
3. **降级选择**: 实例 3 (其他区域: B/zone-1)

### 动态请求示例

```java
@RestController
@RequestMapping("/api")
public class DemoController {
    
    @Autowired
    private ServiceBClient serviceBClient;
    
    @GetMapping("/test")
    public String test() {
        // 使用服务自身的区域和机房
        return serviceBClient.hello();
    }
}
```

使用 curl 测试动态负载均衡:

```bash
# 使用默认配置(A/zone-1)
curl http://localhost:8080/api/test

# 动态指定区域和机房为 B/zone-1
curl -H "X-Region: B" -H "X-Zone: zone-1" http://localhost:8080/api/test
```

## 调试和监控

### 启用 DEBUG 日志

在 `application.yml` 中配置:

```yaml
logging:
  level:
    org.rdlinux.loadbalancer: DEBUG
```

启用后会看到类似以下日志:

```
RegionZoneAwareServiceInstanceListSupplier initialized with localRegion=A, localZone=zone-1
Filtering 3 instances for service: service-b, currentRegion=A, currentZone=zone-1
Found 1 instances in same region(A) and zone(zone-1)
```

### 日志说明

- `initialized with localRegion=X, localZone=Y`: 服务启动时读取的本地区域和机房
- `currentRegion=X, currentZone=Y`: 当前请求使用的区域和机房(可能来自 ThreadLocal)
- `Found N instances in same region(X) and zone(Y)`: 找到的同区域同机房实例数量

## 技术栈

- **Java**: 1.8+
- **Spring Boot**: 2.7.18
- **Spring Cloud**: 2021.0.7
- **Spring Cloud LoadBalancer**: 负载均衡核心

## 注意事项

1. **元数据配置**: 确保所有服务实例都正确配置了 `region` 和 `zone` 元数据
2. **格式规范**: `available-region-zones` 的格式必须严格遵守 `region1:zone1,zone2;region2:zone3,zone4`
3. **降级策略**: 当没有符合条件的实例时,会自动降级到次优策略,确保服务可用性
4. **ThreadLocal 清理**: Filter 会自动在请求结束时清理 ThreadLocal,避免内存泄漏
5. **Filter 优先级**: RegionZoneFilter 的优先级为 `Integer.MIN_VALUE + 6`,确保在链路追踪之后执行

## 常见问题

### Q1: 如何验证负载均衡是否生效?

A: 启用 DEBUG 日志,观察日志中的 `currentRegion` 和 `currentZone` 是否符合预期。

### Q2: 请求头不生效怎么办?

A: 检查以下几点:
1. 确保请求头名称为 `X-Region` 和 `X-Zone`
2. 确保 Filter 已正确注册(查看启动日志)
3. 确保请求经过了 Filter(非 Feign 内部调用)

### Q3: 支持哪些注册中心?

A: 支持所有 Spring Cloud 兼容的注册中心,包括:
- Eureka
- Nacos
- Consul
- Zookeeper

### Q4: 如何禁用动态负载均衡?

A: 只需不在请求头中传递 `X-Region` 和 `X-Zone` 即可,系统会自动使用服务实例自身配置的区域和机房。

### Q5: 可以自定义请求头名称吗?

A: 当前版本请求头名称固定为 `X-Region` 和 `X-Zone`,如需自定义,可以修改 `RegionZoneContext` 中的常量。

## 版本历史

### v1.0.0 (2026-01-14)
- ✅ 初始版本发布
- ✅ 支持区域和机房感知的负载均衡
- ✅ 支持动态请求级别的区域机房指定
- ✅ 支持可访问区域配置
- ✅ 支持多种注册中心

## 许可证

Apache License 2.0

## 作者

RDLinux Team
