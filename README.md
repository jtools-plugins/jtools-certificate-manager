# JTools Certificate Manager

IntelliJ IDEA 证书管理插件，提供全面的证书管理、自签名证书生成和SSL证书监控功能。

## 功能特性

### 📜 证书管理
- 支持导入多种格式证书：PEM、CRT、CER、JKS、PKCS12
- 支持导出为 PEM、JKS、PKCS12、PKCS7 格式
- 证书详情查看（颁发者、有效期、指纹等）
- 证书链验证
- 私钥导出
- 证书过期状态高亮显示（红色=已过期，橙色=即将过期）

### 🔐 自签名证书
- 基于YAML配置生成CA根证书
- 使用CA签发服务器证书
- 支持RSA/EC等多种算法
- 支持SAN扩展（DNS、IP）
- 一键导出功能：
  - 按内容选择：仅服务器证书 / 仅CA证书 / 全部
  - 按格式选择：PEM / JKS / PKCS12
  - 自动生成使用说明文档

### 📡 证书监控
- 监控远程域名SSL证书有效期
- 支持批量添加域名
- 自动定时刷新（可配置间隔）
- 证书状态颜色标识
- 导出监控报告（文本/CSV格式）

## 安装

1. 打开 IntelliJ IDEA
2. 进入 `Settings` → `Plugins` → `Marketplace`
3. 搜索 "JTools Certificate" 并安装
4. 重启 IDE

## 使用说明

### 证书管理
1. 打开工具窗口：`View` → `Tool Windows` → `Certificate`
2. 点击 `+` 按钮导入证书
3. 右键证书可查看详情、导出、验证等操作

### 自签名证书
1. 切换到"自签证书"标签页
2. 点击"导入模板配置"加载YAML模板
3. 修改配置（域名、有效期等）
4. 点击"生成CA证书"创建根证书
5. 点击"根据CA生成https证书"创建服务器证书
6. 点击"导出证书..."选择导出方式

### 证书监控
1. 切换到"证书监控"标签页
2. 点击"添加"输入要监控的域名
3. 点击"刷新"获取证书信息
4. 可设置自动刷新间隔

## 导出文件说明

### PEM格式导出
```
server/
  ├── server.pem        # 服务器证书
  ├── server-key.pem    # 服务器私钥
  ├── fullchain.pem     # 完整证书链（Nginx推荐使用）
  └── README.txt        # 使用说明
ca/
  ├── ca.pem            # CA根证书（导入客户端信任）
  ├── ca-key.pem        # CA私钥（妥善保管）
  └── README.txt        # 使用说明
```

### JKS/P12格式导出
```
server/
  ├── server.jks/p12    # 服务器证书（含私钥和证书链）
  └── README.txt        # 使用说明
ca/
  ├── ca.jks/p12        # CA证书（含私钥）
  ├── ca-truststore.jks/p12  # CA信任库（仅公钥，用于客户端）
  └── README.txt        # 使用说明
密码.txt                # 证书密码信息
```

## 配置示例

```yaml
# 签名算法: RSA, EC
algorithm: RSA

# CA根证书配置
ca:
  dn: CN=My Root CA,O=MyOrg,C=CN
  validityYear: 10

# 服务器证书配置
certificate:
  dn: CN=localhost,O=MyOrg,C=CN
  validityYear: 1
  san:
    dns:
      - localhost
      - "*.example.com"
    ip:
      - 127.0.0.1
```

## 版本历史

### v2.0.3
- 新增证书监控功能
- 优化自签证书导出流程，区分CA证书和服务器证书
- 添加证书验证功能
- UI美化：状态颜色、表格样式优化
- 增强错误处理和用户提示

### v2.0.0
- 重构证书管理界面
- 添加证书过期状态显示
- 支持更多导出格式

### v1.0.0
- 初始版本
- 基础证书导入导出功能

## License

MIT License
