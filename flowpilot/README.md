# flowpilot 后端

Java21、Spring Boot3.5.16、Spring AI1.1.8、MyBatis-Plus、MySQL8.4，11个Maven子模块，一个启动应用。

```sh
mvn -f flowpilot/pom.xml clean verify
python3 scripts/dev-backend.py
```

第二条从项目根目录执行；先按 deploy/README.md 启动本地数据库与Redis并生成环境文件。主程序为 app/target/app-1.0.0.jar。生产自动部署见根 README。
