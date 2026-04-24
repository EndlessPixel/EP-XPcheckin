# EP-XPcheckin

> 一个简洁、公平、可管理、支持多语言的 Minecraft 每日XP签到插件，适配多版本服务端，配置灵活，无需复杂操作即可快速启用。

## 功能特性
- 公平经验分布
1~10 位数经验均匀随机，每段位 10% 概率，大奖稀有且合理，兼顾趣味性与平衡性。
      
- 连续签到加成
每多连续 1 天额外获得 10% 经验，断签自动重置，鼓励玩家每日参与。
      
- 进服签到提醒
当日未签到时，玩家进服后自动发送提醒，可通过指令自由开启/关闭。
      
- 多语言支持
默认包含中文（zh-CN）、英文（en）双语言包，可在配置文件中一键切换，支持后续扩展更多语言。
      
- 灵活配置
冷却时间、广播阈值、连续签到倍率等均可通过 config.yml 自定义，无需修改代码。

- 玩家指令
  - `/checkin` —— 完成今日签到，领取随机经验
  - `/checkin on/off` —— 开启/关闭签到提醒
  - `/checkin info` —— 查看个人签到记录、连续天数及总经验统计
  - `/checkin top` —— 查看全服签到总经验排行榜（前10名）

- OP 管理指令
  - `/checkin reload` —— 热重载配置文件与语言文件（替代原redata指令，更规范）    
  - `/checkin record <玩家> <日期>` —— 删除指定玩家某日签到记录（支持玩家名/UUID）
  - `/checkin look <玩家名/UUID>` —— 查看任意玩家（含离线）的签到信息
      
- 完整 TAB 补全
子命令、在线玩家、玩家历史签到日期均支持智能补全，提升操作便捷性。

- 数据安全持久化
所有签到数据（含每日明细）存入 `player.yml`，自动异步保存，不丢失、不乱码，支持手动重载。
      
- 全服广播彩蛋
当玩家签到获得经验达到设定阈值时，自动发送全服广播，增强游戏氛围。
      
权限说明
- 玩家指令（/checkin、/checkin on/off、/checkin info、/checkin top）：无权限要求，所有玩家默认可使用。
- 管理指令（/checkin reload、/checkin record、/checkin look）：仅 OP 可执行，无需额外配置权限。
文件结构
插件所有文件均自动生成，路径如下：
```
plugins/EP-XPcheckin/
├─ config.yml       # 主配置文件（冷却、倍率、语言等设置）
├─ player.yml       # 玩家签到数据文件（核心数据，自动保存）
└─ lang/            # 语言文件目录
   ├─ zh-CN.yml     # 中文语言包（默认启用）
   └─ en.yml        # 英文语言包（备用，可一键切换）
```

## 数据说明
`player.yml` 中包含每位玩家的完整签到数据，具体如下：
- 最后签到日期（lastCheckInDate）
- 连续签到天数（streak）
- 总签到次数（totalTimes）
- 总获得经验（totalXP）
- 每日签到明细（records）：含当日获得经验、连续天数
- 签到提醒开关状态（remind）
版本支持
- Minecraft 版本：1.12+ ~ 1.21+（兼容主流版本）
- 服务端支持：Spigot、Paper 及衍生服务端（推荐 Paper 以获得更好性能）
- 插件版本：v1.2（正式版，双语言完整支持）
使用说明
1. 将编译好的 `EP-XPcheckin-1.2.jar` 放入服务器 `plugins` 目录。
2. 重启服务器，插件自动生成配置文件、语言文件及数据文件。
3. （可选）修改 `config.yml` 自定义插件参数，修改后执行 `/checkin reload` 热重载生效。
4. 游戏内输入 `/checkin` 即可开始每日签到，所有指令支持 TAB 补全。

# 配置说明（config.yml 关键参数）

## 语言设置（默认zh-CN，切换英文改为en）
```yaml
language:
  default: zh-CN
```

## 功能设置
```yaml
settings:
  command-cooldown: 5          # 指令冷却（单位：tick，20tick=1秒）
  join-remind-delay: 20        # 进服提醒延迟（单位：tick）
  streak-multiplier: 0.1       # 连续签到倍率（0.1=每天+10%）
  broadcast-above-xp: 1000000  # 触发全服广播的经验阈值
```

# 作者信息
EndlessPixel Studio BY system_mini
插件持续维护更新，如有bug反馈或功能建议，可联系作者优化。