package com.appliedenhancements;

import com.appliedenhancements.api.AelisCycleSeedPolicy;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Unified configuration for Applied Enhancements. */
public final class Config {
    public static final ModConfigSpec SPEC;

    // Crafting
    public static final ModConfigSpec.EnumValue<CraftingOrderMode> MAX_CRAFTING_ORDER_AMOUNT;
    public static final ModConfigSpec.BooleanValue ENABLE_PROGRESS_DISPLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_ENHANCED_MATERIAL_CALCULATION;

    // Crafting / AELIS
    public static final ModConfigSpec.BooleanValue ENABLE_AUTOMATIC_AELIS_PLANNER;
    public static final ModConfigSpec.BooleanValue ENABLE_AELIS_PLAN_SCREEN;
    public static final ModConfigSpec.BooleanValue ENABLE_AELIS_BIG_INTEGER_PLANNING;
    public static final ModConfigSpec.BooleanValue AELIS_PREFER_PLANNABLE_PRODUCER;
    public static final ModConfigSpec.BooleanValue AELIS_CHUNK_RETURNED_ITEM_FEEDBACK;
    public static final ModConfigSpec.BooleanValue ENABLE_CHANCE_OUTPUT_BONUS;
    public static final ModConfigSpec.BooleanValue ENABLE_CHANCE_TOP_UP;
    public static final ModConfigSpec.BooleanValue CHANCE_TOP_UP_CRAFT_INPUTS;
    public static final ModConfigSpec.IntValue CHANCE_TOP_UP_IDLE_SECONDS;
    public static final ModConfigSpec.IntValue CHANCE_TOP_UP_MAX_FACTOR;
    public static final ModConfigSpec.BooleanValue ENABLE_PROVIDER_OUTPUT_PULL;
    public static final ModConfigSpec.IntValue PROVIDER_OUTPUT_PULL_EXPIRY_SECONDS;

    // Compatibility fixes for other mods (module: com.appliedenhancements.matrixfix)
    public static final ModConfigSpec.BooleanValue FIX_MATRIX_CRAFTER_COOLDOWN;

    // Diagnostics (module: com.appliedenhancements.diag)
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS_ENABLED;
    public static final ModConfigSpec.IntValue AELIS_MAX_NODES;
    public static final ModConfigSpec.IntValue AELIS_COMPILE_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue AELIS_DIAGNOSTICS;
    public static final ModConfigSpec.IntValue CYCLE_SOLVER_MAX_SCC_NODES;
    public static final ModConfigSpec.IntValue CYCLE_SOLVER_MAX_SEARCH_STATES;
    public static final ModConfigSpec.IntValue CYCLE_SOLVER_BUDGET_MS;
    public static final ModConfigSpec.EnumValue<AelisCycleSeedPolicy> CYCLE_SEED_POLICY;

    // Performance
    public static final ModConfigSpec.BooleanValue ENABLE_PATTERN_CACHING;
    public static final ModConfigSpec.IntValue PATTERN_CACHE_SIZE;
    public static final ModConfigSpec.BooleanValue ENABLE_STORAGE_BUS_SLOT_INDEX;
    public static final ModConfigSpec.BooleanValue ENABLE_IO_BUS_OPTIMIZATION;

    // Storage
    public static final ModConfigSpec.BooleanValue ENABLE_INFINITE_STORAGE_LIMIT_BYPASS;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment(
                "AE2 crafting behavior and presentation",
                "AE2 合成行为与界面显示")
                .translation("appliedenhancements.config.section.crafting")
                .push("crafting");

        MAX_CRAFTING_ORDER_AMOUNT = builder
                .comment(
                        "Quantity input limit: DISABLED leaves host input unchanged, LONG_MAX allows long, BIG_INTEGER allows exact input.",
                        "Submission continues through the host confirmation menu and does not select a planner.",
                        "数量输入上限：DISABLED 沿用宿主输入，LONG_MAX 扩展至 long，BIG_INTEGER 允许任意精度输入。",
                        "提交后沿用宿主确认菜单，不根据数量选择规划器。",
                        "Allowed values / 可选值: DISABLED, LONG_MAX, BIG_INTEGER",
                        "Default / 默认值: DISABLED")
                .translation("appliedenhancements.config.max_crafting_order_amount")
                .defineEnum("max_crafting_order_amount", CraftingOrderMode.DISABLED);

        ENABLE_PROGRESS_DISPLAY = builder
                .comment(
                        "Show crafting calculation progress and selected planner path.",
                        "显示合成计算进度与所选规划路径。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_progress_display")
                .define("enable_progress_display", false);

        ENABLE_ENHANCED_MATERIAL_CALCULATION = builder
                .comment(
                        "Enable enhanced material calculation in the crafting preview.",
                        "Shows accurate missing, stored, and crafting item counts.",
                        "在合成预览中启用增强材料计算。",
                        "显示准确的缺失、库存与待合成物品数量。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_enhanced_material_calculation")
                .define("enable_enhanced_material_calculation", false);

        builder.comment(
                "AELIS planner settings",
                "AELIS 规划器设置")
                .translation("appliedenhancements.config.section.aelis")
                .push("aelis");

        ENABLE_AUTOMATIC_AELIS_PLANNER = builder
                .comment(
                        "Enable automatic AELIS integration in AE2's native planner.",
                        "Third-party API calls are not affected by this setting.",
                        "Manual crafting-plan inventory reservations are active only while this is enabled.",
                        "在 AE2 原生规划流程中自动启用 AELIS。",
                        "第三方 API 调用不受此设置影响。",
                        "手动合成计划库存预留仅在此项启用时生效。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_automatic_aelis_planner")
                .define("enable_automatic_planner", false);

        ENABLE_AELIS_PLAN_SCREEN = builder
                .comment(
                        "Use the AELIS crafting-plan screen on this client.",
                        "Disable to use AE2's original confirmation screen without changing planning or execution.",
                        "在当前客户端使用 AELIS 合成计划新界面。",
                        "关闭后使用 AE2 原版确认界面，不影响规划与合成执行。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.enable_aelis_plan_screen")
                .define("enable_plan_screen", true);

        ENABLE_AELIS_BIG_INTEGER_PLANNING = builder
                .comment(
                        "Use exact BigInteger arithmetic for AELIS orders and intermediate demands above Long.MAX_VALUE.",
                        "Long projections are for compatibility; CPUs decide execution and can read the exact execution API.",
                        "When disabled, automatic AELIS uses legacy long arithmetic; explicit exact API requests remain exact.",
                        "对超过 Long.MAX_VALUE 的订单和 AELIS 中间需求使用精确 BigInteger 算术。",
                        "long 投影仅供兼容；由 CPU 自行决定执行，并可读取精确执行 API。",
                        "关闭后，自动 AELIS 使用原有 long 算术；显式精确 API 请求仍使用大整数。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.enable_aelis_big_integer_planning")
                .define("enable_big_integer_planning", true);

        AELIS_PREFER_PLANNABLE_PRODUCER = builder
                .comment(
                        "If AELIS cannot plan an item's first pattern but can plan a later one, use the later pattern.",
                        "Without this, AELIS hands the whole request back to AE2's planner. Changes which pattern is used compared with AE2's order.",
                        "若 AELIS 无法规划某物品的首个样板但可规划之后的样板，则改用之后的样板。",
                        "关闭时，AELIS 会把整个请求交还给 AE2 规划器。与 AE2 的顺序相比，会改变所用的样板。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.aelis_prefer_plannable_producer")
                .define("prefer_plannable_producer", false);

        AELIS_CHUNK_RETURNED_ITEM_FEEDBACK = builder
                .comment(
                        "Let AELIS plan chains where a returned item (empty bucket, glass bottle) is used again in the same plan.",
                        "Crafts run in chunks sized by the returned items in stock, so several machines can work in parallel.",
                        "The CPU then takes up to that many returned items from storage and gives them back when the job ends.",
                        "允许 AELIS 规划返还物品（空桶、玻璃瓶）在同一计划中再次被使用的合成链。",
                        "合成按库存中返还物品的数量分批进行，使多台机器可以并行工作。",
                        "合成处理器会从存储中取出相应数量的返还物品，并在任务结束时归还。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.aelis_chunk_returned_item_feedback")
                .define("chunk_returned_item_feedback", false);

        AELIS_MAX_NODES = builder
                .comment(
                        "Maximum nodes analyzed by AELIS per attempt.",
                        "Higher values allow more complex recipes but use more memory.",
                        "AELIS 每次尝试允许分析的最大节点数。",
                        "数值越高，可处理的配方越复杂，但会占用更多内存。",
                        "Range / 取值范围: 1000 ~ 1000000",
                        "Default / 默认值: 100000")
                .translation("appliedenhancements.config.aelis_max_nodes")
                .defineInRange("max_nodes", 100000, 1000, 1000000);

        AELIS_COMPILE_BUDGET_MS = builder
                .comment(
                        "Compilation budget in milliseconds for each AELIS attempt.",
                        "Limits time spent analyzing the crafting tree before execution.",
                        "每次 AELIS 尝试的编译时间预算，单位为毫秒。",
                        "限制执行前分析合成树所花费的最长时间。",
                        "Range / 取值范围: 100 ~ 30000",
                        "Default / 默认值: 2000")
                .translation("appliedenhancements.config.aelis_compile_budget_ms")
                .defineInRange("compile_budget_ms", 2000, 100, 30000);

        // Existing OmniSequence versions link this field at runtime.
        AELIS_DIAGNOSTICS = builder
                .comment(
                        "Enable diagnostics in integrations that read this compatibility setting.",
                        "Applied Enhancements no longer emits temporary planning traces.",
                        "为读取此兼容配置的联动模组启用诊断日志。",
                        "Applied Enhancements 已移除临时规划日志。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.aelis_diagnostics")
                .define("enable_diagnostics", false);

        builder.comment(
                "Bounded solver for cyclic crafting dependencies",
                "循环合成依赖的有界求解器")
                .translation("appliedenhancements.config.section.cycle_solver")
                .push("cycle_solver");

        CYCLE_SOLVER_MAX_SCC_NODES = builder
                .comment(
                        "Maximum material nodes in one strongly connected component.",
                        "单个强连通分量允许包含的最大材料节点数。",
                        "Range / 取值范围: 4 ~ 1024",
                        "Default / 默认值: 256")
                .translation("appliedenhancements.config.cycle_solver_max_scc_nodes")
                .defineInRange("max_scc_nodes", 256, 4, 1024);

        CYCLE_SOLVER_MAX_SEARCH_STATES = builder
                .comment(
                        "Maximum branch-search states for multi-candidate cyclic components.",
                        "多候选循环分量允许搜索的最大分支状态数。",
                        "Range / 取值范围: 1000 ~ 10000000",
                        "Default / 默认值: 1000000")
                .translation("appliedenhancements.config.cycle_solver_max_search_states")
                .defineInRange("max_search_states", 1_000_000, 1_000, 10_000_000);

        CYCLE_SOLVER_BUDGET_MS = builder
                .comment(
                        "Maximum time spent by one global or local cyclic solve in milliseconds.",
                        "单次全图或局部循环求解允许使用的最大时间（毫秒）。",
                        "Range / 取值范围: 10 ~ 5000",
                        "Default / 默认值: 1000")
                .translation("appliedenhancements.config.cycle_solver_budget_ms")
                .defineInRange("budget_ms", 1000, 10, 5000);

        CYCLE_SEED_POLICY = builder
                .comment(
                        "Policy for cycle startup materials after the proven cycle schedule finishes.",
                        "PRESERVE_MINIMUM keeps the minimum seed vector for the next order.",
                        "MAX_THROUGHPUT allows the current order to consume every cycle material.",
                        "已证明循环顺序执行完成后，启动材料的处理策略。",
                        "PRESERVE_MINIMUM 会为下一份订单保留最低种子向量。",
                        "MAX_THROUGHPUT 允许当前订单使用全部循环材料。",
                        "Allowed values / 可选值: PRESERVE_MINIMUM, MAX_THROUGHPUT",
                        "Default / 默认值: PRESERVE_MINIMUM")
                .translation("appliedenhancements.config.cycle_seed_policy")
                .defineEnum("seed_policy", AelisCycleSeedPolicy.PRESERVE_MINIMUM);

        builder.pop();

        ENABLE_CHANCE_OUTPUT_BONUS = builder
                .comment(
                        "Count extra (chance) outputs of marked processing patterns toward the requested total.",
                        "Only patterns marked as chance-output patterns are affected.",
                        "Requires the marker toggle on the processing encoding panel.",
                        "为已标记的处理样板计算额外（概率）产物，并计入请求的总数。",
                        "仅影响已标记为概率产物的处理样板。",
                        "需要在处理编码面板上启用标记开关。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_chance_output_bonus")
                .define("enable_chance_output_bonus", false);

        ENABLE_CHANCE_TOP_UP = builder
                .comment(
                        "When all crafts of a marked processing pattern are pushed and no output arrives for the idle time,",
                        "push more crafts (sized by the pattern's learned yield, inputs taken from network storage) until",
                        "the requested amount is reached. Requires the chance output bonus to be enabled.",
                        "当已标记处理样板的所有合成均已推送且在空闲时间内没有产物到达时，",
                        "按该样板学习到的产出率追加合成（输入取自网络存储），直到达到请求数量。需要同时启用概率产物计数。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_chance_top_up")
                .define("enable_chance_top_up", false);

        CHANCE_TOP_UP_CRAFT_INPUTS = builder
                .comment(
                        "When a top-up wave lacks inputs that the network can craft (for example a dust ground from stock),",
                        "start a normal crafting job for the missing amount; the next wave then finds the items in storage.",
                        "当追加合成缺少网络可以合成的输入（例如由库存研磨的粉末）时，为缺少的数量发起一个普通合成任务，",
                        "下一轮即可在存储中找到这些物品。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.chance_top_up_craft_inputs")
                .define("chance_top_up_craft_inputs", true);

        CHANCE_TOP_UP_IDLE_SECONDS = builder
                .comment(
                        "Seconds without any output (after every craft was pushed) before a top-up wave starts.",
                        "在所有合成均已推送后，多少秒没有产物到达才开始追加一轮合成。",
                        "Range / 取值范围: 1 ~ 3600",
                        "Default / 默认值: 30")
                .translation("appliedenhancements.config.chance_top_up_idle_seconds")
                .defineInRange("chance_top_up_idle_seconds", 30, 1, 3_600);

        CHANCE_TOP_UP_MAX_FACTOR = builder
                .comment(
                        "A job may add at most this many times its planned crafts of a pattern as top-up crafts.",
                        "单个任务追加的合成数最多为该样板计划合成数的这个倍数。",
                        "Range / 取值范围: 1 ~ 1000",
                        "Default / 默认值: 10")
                .translation("appliedenhancements.config.chance_top_up_max_factor")
                .defineInRange("chance_top_up_max_factor", 10, 1, 1_000);

        builder.pop();
        builder.pop();

        builder.comment(
                "Performance optimizations with validation and native fallbacks",
                "带重新验证与原版回退的性能优化")
                .translation("appliedenhancements.config.section.performance")
                .push("performance");

        builder.comment(
                "Encoded-pattern cache settings",
                "编码样板缓存设置")
                .translation("appliedenhancements.config.section.pattern_cache")
                .push("pattern_cache");

        ENABLE_PATTERN_CACHING = builder
                .comment(
                        "Cache pattern input validation and container-return lookups.",
                        "Significantly improves large crafting calculations.",
                        "缓存样板输入有效性与容器返还物查询。",
                        "可显著提升大型合成计算的性能。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.enable_pattern_caching")
                .define("enabled", true);

        PATTERN_CACHE_SIZE = builder
                .comment(
                        "Maximum multi-key cache entries retained per pattern.",
                        "Limits memory use while preserving useful cache diversity.",
                        "每张样板保留的多键缓存最大条目数。",
                        "在保留有效缓存多样性的同时限制内存占用。",
                        "Range / 取值范围: 8 ~ 256",
                        "Default / 默认值: 32")
                .translation("appliedenhancements.config.pattern_cache_size")
                .defineInRange("max_entries_per_pattern", 32, 8, 256);

        builder.pop();

        builder.comment(
                "Item storage-bus optimization",
                "物品存储总线优化")
                .translation("appliedenhancements.config.section.storage_bus")
                .push("storage_bus");

        ENABLE_STORAGE_BUS_SLOT_INDEX = builder
                .comment(
                        "Index candidate slots during AE2's existing external-inventory scan.",
                        "Cached slots are revalidated; incomplete results fall back to AE2's full scan.",
                        "在 AE2 原有外部库存扫描期间建立候选槽位索引。",
                        "缓存槽位会重新验证；结果不完整时回退 AE2 完整扫描。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.enable_storage_bus_slot_index")
                .define("enable_slot_index", true);

        builder.pop();

        builder.comment(
                "Import and export bus optimization",
                "输入与输出总线优化")
                .translation("appliedenhancements.config.section.io_bus")
                .push("io_bus");

        ENABLE_IO_BUS_OPTIMIZATION = builder
                .comment(
                        "Reuse validated source and target slot hints for AE2 I/O buses.",
                        "Incomplete operations fall back to AE2's original slot routing.",
                        "为 AE2 输入与输出总线复用经过验证的来源及目标槽位提示。",
                        "操作结果不完整时回退 AE2 原始槽位路由。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.enable_io_bus_optimization")
                .define("enable_slot_routing", true);

        builder.pop();
        builder.pop();

        builder.comment(
                "Storage behavior and representation",
                "存储行为与显示")
                .translation("appliedenhancements.config.section.storage")
                .push("storage");

        builder.comment(
                "Explicitly marked infinite storage cells",
                "显式标记的无限存储元件")
                .translation("appliedenhancements.config.section.infinite")
                .push("infinite");

        ENABLE_INFINITE_STORAGE_LIMIT_BYPASS = builder
                .comment(
                        "Allow unlimited extraction and planning supply for explicitly marked infinite cells.",
                        "Only keys accepted by the mounted cell's extraction checks are supplied; finite cells are unchanged.",
                        "Disable this to preserve the original cell behavior and reported quantities.",
                        "显式标记的无限元件可无限取出，规划中作为无限材料来源。",
                        "仅提供元件允许取出的物品，不改变普通有限元件。",
                        "关闭后保留元件原实现的行为与数量。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_infinite_storage_limit_bypass")
                .define("enable_listing_limit_bypass", false);

        builder.pop();
        builder.pop();

        builder.comment(
                "Pattern provider behavior",
                "样板供应器行为")
                .translation("appliedenhancements.config.section.pattern_provider")
                .push("pattern_provider");

        ENABLE_PROVIDER_OUTPUT_PULL = builder
                .comment(
                        "Allow pattern providers to pull the outputs of their crafts back out of machines that keep them.",
                        "Each provider face is configured in the provider's side panel (off by default). Only declared",
                        "pattern outputs that are owed are pulled, never anything that is an input of the provider's patterns.",
                        "允许样板供应器从保留产物的机器中取回其合成的产物。",
                        "每个面在供应器的侧面板中单独设置（默认关闭）。只取回尚未返回的样板声明产物，",
                        "绝不取出供应器样板的任何输入物。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_provider_output_pull")
                .define("enable_output_pull", false);

        PROVIDER_OUTPUT_PULL_EXPIRY_SECONDS = builder
                .comment(
                        "Forget owed outputs of a provider face after this many seconds without any output arriving",
                        "(cancelled jobs, chance outputs that never come).",
                        "若某面在此秒数内没有任何产物到达，则放弃记录的待取回产物（已取消的任务、未产出的概率产物）。",
                        "Range / 取值范围: 10 ~ 86400",
                        "Default / 默认值: 300")
                .translation("appliedenhancements.config.provider_output_pull_expiry_seconds")
                .defineInRange("output_pull_expiry_seconds", 300, 10, 86_400);

        builder.pop();

        builder.comment(
                "Fixes for bugs in other mods",
                "修复其他模组的问题")
                .translation("appliedenhancements.config.section.compat_fixes")
                .push("compat_fixes");

        FIX_MATRIX_CRAFTER_COOLDOWN = builder
                .comment(
                        "ExtendedAE Assembler Matrix: count down the crafters' output hand-over cooldown. Without this, one",
                        "partial hand-over (for example full storage) stops that crafter from ever delivering outputs again",
                        "until the world is reloaded, so crafting jobs hang.",
                        "扩展 AE 装配矩阵：让合成器的产物交付冷却正常倒计时。关闭后，一次部分交付（例如存储已满）",
                        "会使该合成器在重新加载世界前再也无法交付产物，导致合成任务卡住。",
                        "Default / 默认值: true")
                .translation("appliedenhancements.config.fix_matrix_crafter_cooldown")
                .define("fix_extendedae_matrix_crafter_cooldown", true);

        builder.pop();

        builder.comment(
                "Debug diagnostics",
                "调试诊断")
                .translation("appliedenhancements.config.section.diagnostics")
                .push("diagnostics");

        DIAGNOSTICS_ENABLED = builder
                .comment(
                        "Write [ACE/diag] debug lines to the log (crafting CPU stalls and refusals, ExtendedAE Assembler Matrix",
                        "state, pattern pushes). Costs some performance and log space: only switch on while debugging.",
                        "在日志中输出 [ACE/diag] 调试信息（合成 CPU 停滞与拒收、扩展 AE 装配矩阵状态、样板推送）。",
                        "会占用一定性能和日志空间：仅在调试时开启。",
                        "Default / 默认值: false")
                .translation("appliedenhancements.config.enable_diagnostics_log")
                .define("enabled", false);

        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }
}
