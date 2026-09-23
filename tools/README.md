# Tools

本目录保存项目开发工具的实现文件。正式入口由 Gradle task 提供，本目录不提供独立执行入口。

## 目录

- `checks/`：项目 Java 静态规则检查器、规则实现和规则 fixture。
- `formatter/`：文本、换行、缩进等 Python 格式化脚本。

## checks 实现

### 目录职责

- `project/`：项目源码规则运行域，包含 Gradle 调用入口、规则 runner、规则输入和规则结果。
- `project/rules/`：项目源码规则实现；规则实现 `ProjectRule`，由 `ProjectRules` 注册并由项目源码规则入口执行。
- `project/fixture/`：项目源码规则 fixture 运行域，包含 fixture 用例模型、运行结果和 fixture runner。
- `project/model/`：项目源码规则共享的数据模型。
- `project/analysis/`：项目源码规则共享的源码语义分析和索引。
- `project/support/`：项目源码规则共享的无状态读取、解析、输出和 AST 辅助函数。
- `integrity/`：检查器自身完整性运行域，包含 Gradle 调用入口、规则 runner 和规则输入模型。
- `integrity/rules/`：检查器自身完整性规则实现和注册表；规则实现 `IntegrityRule`，由 `IntegrityRules` 注册并由检查器自检入口执行。
- `integrity/model/`：检查器自身完整性规则共享的数据模型。
- `integrity/support/`：检查器自身完整性规则共享的无状态读取和输出辅助函数。
- `fixtures/`：项目静态规则的正反例；每个 fixture 通过 `expected-rules.txt` 声明预期命中的规则，ok fixture 必须用 `# covers:` 声明覆盖的规则。

### 项目源码规则

- `ColorAllocationRule.java`：禁止在非 `static final` 字段位置创建字面量 `Color`。
- `ProjectileSourceChainedCallRule.java`：禁止直接链式调用 projectile 或 missile 的 `getSource()` 返回值。
- `ProjectileSourceFieldCacheRule.java`：禁止把 projectile 或 missile 的 source 缓存为字段。
- `MissingLifecycleEntryAnnotationRule.java`：要求生命周期控制器静态入口显式标记 `@LifecycleEntry`。
- `LifecycleTypeResolutionFailureRule.java`：要求带生命周期式自注册形态的类必须能解析其生命周期接口继承链，避免引用穿透失败后静默漏报。
- `LifecycleConstructorRule.java`：禁止带生命周期入口的控制器暴露 `public` 构造函数。
- `FieldAccessBoundaryRule.java`：检查实例字段访问边界：字段必须显式声明访问修饰符，`public` 实例字段只允许在 `@PublicData` 类型中，`protected` 实例字段只允许在源码内存在子类的类型中。
- `PublicDataShapeRule.java`：检查 `@PublicData` 类型形态：不得声明普通方法，不得继承其它类，不得实现接口。功能类、控制器、生命周期对象和渲染对象不得用该注解放行字段访问。
- `StaticFieldImmutabilityRule.java`：要求所有 `static` 字段都是 `final`；运行期可变状态应属于实例或显式运行期存储，而不是可重新绑定的类变量。

### 自检规则

- `RuleRegistrySyncRule.java`：检查项目源码规则文件和 `ProjectRules.all()` 注册表同步。
- `FixtureIntegrityRule.java`：检查 fixture 目录形状、违规样例覆盖和 ok 样例覆盖。
- `ReadmeRuleListSyncRule.java`：检查 README 中的项目源码规则列表同步。

## formatter 实现

- `utf8_no_bom.py` 将带 UTF-8 BOM 的文本文件转换为无 BOM UTF-8。
- `format_lines.py` 根据 `--line-ending {lf,crlf}` 将文本文件换行统一为指定格式，默认使用 LF；`--check` 只检查，不写入文件。
- `java_tabs.py` 将 Java 行首四空格缩进转换为 tab 缩进。
- `java_single_final_blank_line.py` 将 Java 文件末尾统一为一个 LF，不保留额外空行。
- `remove_pycache.py` 删除项目中的 `__pycache__` 目录。

## Gradle 任务

- `formatProject`：执行 formatter 并写入修复。
- `verifyFormat`：执行 formatter 检查模式，不写入文件。
- `projectRulesCheck`：执行项目 Java 静态规则。
- `projectRulesFixtureCheck`：执行项目 Java 静态规则 fixture。
- `projectRulesIntegrityCheck`：聚合执行检查器自身完整性检查。
- `fixProject`：格式化、编译并执行项目静态规则。
- `verifyProject`：只检查格式、编译并执行项目静态规则。
