# EP-XPcheckin
一个简洁、公平、可管理的 Minecraft 每日签到插件，经验按位数均匀分布，绝不崩经济。

## 功能特性
- **公平经验分布**
  1~10 位数经验均匀随机，每段位 10% 概率，大奖稀有且合理
- **连续签到加成**
  每多连续 1 天 +10% 经验，断签自动重置
- **进服签到提醒**
  当日未签到时自动提醒
- **玩家指令**
  - `/checkin` —— 今日签到
  - `/checkin on/off` —— 开启/关闭签到提醒
  - `/checkin info` —— 查看个人签到记录与统计
- **OP 管理指令**
  - `/checkin redata` —— 热重载数据文件
  - `/checkin record <玩家> <日期>` —— 删除指定玩家某日签到记录
- **完整 TAB 补全**
  子命令、在线玩家、历史日期均支持智能补全
- **数据安全持久化**
  所有记录存入 `player.yml`，不丢失、不乱码

## 权限
- 玩家指令：无权限要求
- 管理指令：仅 OP 可执行

## 数据文件
插件数据保存在：
```
plugins/EP-XPcheckin/player.yml
```
包含：
- 最后签到日期
- 连续签到天数
- 总签到次数
- 总获得经验
- 每日签到明细记录

## 版本支持
- Minecraft 1.12+ ~ 1.21+
- Spigot / Paper 服务端

## 使用说明
1. 将编译好的 `EP-XPcheckin-xxx.jar` 放入 `plugins`
2. 重启服务器
3. 游戏内直接使用 `/checkin` 开始签到

---

### 作者
EndlessPixel Studio BY system_mini