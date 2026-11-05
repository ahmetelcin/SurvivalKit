# SurvivalKit — User Guide

Client-side quality-of-life Fabric mod for Minecraft 26.3.

---

## 1. Installation

1. **Requirements:**
   - Minecraft 26.3
   - [Fabric Loader](https://fabricmc.net/) (0.19.5 or higher)
   - Java 25
   - [Fabric API](https://modrinth.com/mod/fabric-api)
2. **Setup:**
   - Place the compiled `survivalkit-<version>.jar` into your `.minecraft/mods/` directory.
   - Minecraft 26.2: use release [0.12.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.12.1-mc26.2) (Fabric Loader 0.19.3 or higher). It does not have the changes after 0.12.1.

---

## 2. Opening the Menu and Keybinds

- **Default Menu Key:** **Right Shift** (`key.survivalkit.menu`).
- **Changing the Key:** Can be rebound via *Options → Controls → Key Binds* under the **SurvivalKit** category.
- **Menu Layout:**
  - **Left Pane:** Module list, search bar, and module enable/disable switches.
  - **Right Pane:** Settings for the selected module, keybind configuration, and collapsible setting groups.
  - **Search:** Search across modules and settings with Turkish-aware case folding.
  - **Top bar:** the profile picker and profile buttons on the left, **Edit HUD** on the right (see section 4 and HUD).
  - **Presets:** at the top of every module's settings; see section 4.
  - **↺:** appears beside a setting that is off its default; a click puts it back. On a setting a preset changed, it first goes back to what it was before the preset.

---

## 3. Modules and Settings

### Auto Eat (`autoeat`)
*Eats for you when hunger drops below your threshold, and leaves the hotbar as it found it.*

- **Common Settings:**
  - **On:** Whether the module is on; saved with the profile.
  - **Key:** The key that turns this module on and off.
- **Trigger:**
  - **Threshold mode:** Fixed or Automatic.
    - *Fixed:* Eats once hunger falls below the hunger threshold.
    - *Automatic:* Waits until the bar has room for all of what the chosen food gives, so not a point of it is wasted: steak (8) at 12, bread (5) at 15.
  - **Hunger threshold:** Starts eating once hunger falls below this (20 is a full bar).
  - **Eat early to keep healing:** While health is below the value set here, eat whenever the hunger bar is not full instead of waiting for hunger, so natural healing never stops. Ordinary food only: golden apples belong to the golden apple rule, which goes first when its own threshold is crossed.
  - **Early eating health:** Eats without waiting for hunger while health is below this (20 is full health).
- **Food:**
  - **Preference order:** Food to eat first, the most wanted at the top. Anything on this list always beats anything that is not.
  - **Blacklist:** Food that is never eaten. The harmful ones and chorus fruit ship blocked; take any of them off.
  - **If none on the list:** If none of the food on the preferred list is in the inventory, which of the rest to eat (*Most filling*, *Lasts longest*, *Junk first*, *Eat nothing*); harmful food always comes last.
- **Golden Apples:**
  - **Use when hurt:** Eat a golden apple when health runs low, whatever hunger says. Golden apples are never picked for hunger on their own; only this rule or the preference order spends them.
  - **Spend enchanted ones too:** Let the health rule spend enchanted golden apples too. Off means plain ones only.
  - **Health threshold:** Reaches for a golden apple once health falls below this (20 is full health).
- **Hands and Inventory:**
  - **Where to look:** Where food may be found (*Hotbar* or *Whole inventory*). With the whole inventory, food from the backpack is swapped into the held slot and put back when the meal is over.
  - **Back to the old slot:** Go back to the hotbar slot that was selected before the meal.
  - **Eating hand:** Which hand the food is eaten from (*Main hand* or *Off hand*). With the off hand, the food is borrowed into it for the length of the meal and the sword or blocks in the main hand are never disturbed; whatever the off hand held comes back afterwards.
  - **Leave an off-hand totem alone:** With a totem of undying in the off hand, eat in the main hand that time and leave the totem where it is.

---

### Auto Totem (`autototem`)
*Keeps a totem of undying in your off hand, and puts the next one there when it is spent.*

- **Trigger:**
  - **When:** When the totem is kept in the off hand.
    - *Always:* Gives that hand over to the totem for good.
    - *Health threshold:* Only borrows it once health runs low.
  - **Health threshold:** The totem goes into the off hand once health falls below this (20 is full health).
  - **Give the hand back:** Put back whatever was in the off hand once health is above the threshold again. Off means the totem stays.
- **Out of Totems:**
  - **Hold instead:** Items to put in the off hand once no totem is left, in order: the first one found in the inventory goes in. Left empty, the hand stays empty. A totem takes the item's place as soon as one turns up.
  - **Warn me:** Put one line in chat when a totem is wanted and none is left (*"Out of totems — the off hand has none."*). A new totem arms the warning again.
- **Hands and Inventory:**
  - **Where to look:** Where totems may be found (*Hotbar* or *Whole inventory*). With the whole inventory, one from the backpack is used too.

---

### Auto Tool (`autotool`)
*Takes out the best tool when you start breaking a block, and puts back what you held once you stop.*

- **Which Tool:**
  - **Durability threshold:** A tool with fewer uses left than this is never taken out, so it does not break in your hand. 0 lets tools be used to the last use.
  - **Leave a sword in hand:** While you hold a sword, hitting a block does not swap it for a tool — a fight is not disarmed by a stray click.
  - **Leave Silk Touch in hand:** A Silk Touch tool in hand stays on the blocks it is the tool for — a pickaxe on stone, a shovel on grass — so you get the block itself. On other blocks the right tool still comes out.
  - **Only with a tool in hand:** Switch only when you already hold a pickaxe, axe, shovel, hoe or shears. With bare hands, food or a block in hand, hitting a block leaves your hand as it is.
- **Enchantments:**
  - **Break with Silk Touch:** For these blocks a Silk Touch tool is taken even if a faster one is in reach. It still has to be able to drop the block.
  - **Break with Fortune:** For these blocks the tool with the highest Fortune is taken even if a faster one is in reach. It still has to be able to drop the block.
- **Hands and Inventory:**
  - **Where to look:** Where tools may be found (*Hotbar* or *Whole inventory*). With the whole inventory, a tool from the backpack is swapped into the slot in hand and swapped back afterwards.
  - **Switch back:** Put back what you held once you let go of attack, or after a second of hitting nothing. A run of blocks is one switch out and one back. Off means the tool stays in hand.

---

### Tool Picker (`toolpicker`)
*Middle-click a block to take the best tool for it. Uses Auto Tool's block lists and durability threshold.*

- **Middle click:** What a middle click on a block does:
  - *Always a tool:* Never picks the block itself; always selects the best tool.
  - *Tool if block not carried:* Picks the block when you carry it, and the tool when you do not.
  - *Off:* Leaves the game's pick block as it is.
  *(Note: In creative mode, vanilla pick block always takes precedence.)*
- **Where to look:** Where tools may be found (*Hotbar* or *Whole inventory*).

---

### World Downloader (`wdl`)
*Writes the chunks you walk through on a server to disk; open them in singleplayer afterwards.*

- **Settings:**
  - **Keep adding to the same world:** Every download from the same server goes into one world, named after its address. Chunks seen again are updated; the ones not seen this time stay as they were, and chests opened before keep their contents. Off starts a new world, stamped with the date, each time.
  - **Seed:** Paste it here if the server answers `/seed`; left empty, 0 is written.
  - **Auto query seed:** Ask the server for `/seed` automatically on connect if the player has permission, and write the answer to the seed setting.
  - **Ownership:** How to handle ownership of tamed entities (dogs, cats, horses, etc.) when saving:
    - *As is:* Keeps existing owner UUIDs.
    - *Transfer to me:* Transmits ownership to your local player UUID.
  - **Flush interval (sec):** How often loaded chunks are saved and flushed to disk, in seconds.
  - **Start with own inventory and position:** When opening the downloaded world in singleplayer, start with your inventory, equipment, position, XP, and health from the server; if disabled, standard world spawn is used.
- **Client Commands (`/wdl`):**
  - `/wdl start` — Starts world downloading.
  - `/wdl stop` — Stops world downloading and reports saved chunk count and path.
  - `/wdl status` — Displays download status and saved chunk count.
  - `/wdl save` — Flushes currently loaded chunks and world state to disk immediately.
  - `/wdl help` — Displays command usage help.
- **Server Permissions:**
  If the server forbids downloading via the permission channel (`wdl:init`), downloading is prevented and the player is notified in chat:
  *"This server does not allow downloading the world, so nothing is being saved."*

---

### Auto Weapon (`autoweapon`)
*With a weapon in hand, takes out the best one for whatever is under the crosshair: Smite for the undead, Bane of Arthropods for spiders, Sharpness for the rest.*

- **Weapons:**
  - **Reach for:** Which weapons may be taken out (*Swords*, *Axes*, *Swords and axes*). An axe hits harder but recovers slower; swords only by default.
  - **Durability threshold:** A weapon with fewer uses left than this is never taken out, so it does not break mid-fight (0 lets weapons be used to the last use).
- **Selection:**
  - **Pick by:** What the weapon in hand is picked by (*Aim only* or *Aim, else the majority around*; the second by default). A mob under the crosshair always comes first. With the second option and nothing aimed at, the hostile mobs in range are counted by their best weapon and the majority's weapon is taken in hand; on a tie the nearest mob decides. A new majority has to last at least a second before the weapon changes.
  - **Range:** How many blocks away hostile mobs still count, for the majority and for the ready slot (8 by default). Mobs that are not hostile, like cows and villagers, and players never count.
- **On Aim:**
  - **Targets:** Who a weapon is picked for (*Mobs* or *Mobs and players*). Mobs only by default; other players only when you choose so.
  - **Auto hit:** Hit what the crosshair is on as soon as the attack cooldown is full, never before. Only what you aim at; nothing is aimed for you.
- **Ready Slot:**
  - **Which slot:** Hotbar slot (1-9) where the best weapon for the majority of hostile mobs around is put before you aim, from anywhere in the inventory (0 is off). What you hold is never changed by it.
  - **Select it too:** Also take the readied slot in hand. Off by default: the weapon waits in its slot and your hand stays as it is.
- **Fight Lock:**
  - **Seconds:** For this long after you hit something or take a hit, no weapon change is made that would reset the attack cooldown. Switching between two of the same item (say, two iron swords) does not reset it, so that is allowed, and so is any switch once the cooldown is already full. 0 is off.
  - **Hold the weapon on aim too:** The lock also holds back the switch on aim and the switch for the majority around. Off, the best weapon always comes out on aim, even if that resets the cooldown.

---

### Vein Breaker (`veinminer`)
*Breaks a whole tree or ore vein at once through the server's own commands (`/loot`, `/give`, `/fill`, `/setblock` — whichever you may run). Does nothing on a server that does not give you `/setblock`.*

- **Trigger:**
  - **Break key:** The key that sets it off while the module is on. Unbound by default: nothing happens until you pick one.
  - **Mode:**
    - *Hold and break* (default): Hold the key and break a log or ore the usual way, the rest of the tree or vein follows.
    - *Look and press:* Look at it and press the key once; press again to stop halfway.
  - **Show outline:** Draws a frame round every block that would go. No frame means nothing would go — not in the list, not a natural tree, or no `/setblock`.
- **Target:**
  - **Blocks:** What a tree or vein may be made of. Only blocks of the same kind as the one you start from are followed; an ore and its deepslate version count as one kind.
  - **At most:** No more blocks than this go at once (128 by default), nearest first, counting the one you start from.
  - **Natural trees only:** Logs are only broken when leaves the world grew touch them, so a log house next to a tree is left alone. Placed leaves do not count.
- **Server:**
  - **Items:** *Into the inventory* or *Where the block was*. When the server lets you run `/loot`, Fortune and Silk Touch on your main hand count. Without `/loot`: with Silk Touch the blocks themselves come with `/give`; with Fortune on an ore the drops are rolled by vanilla's own rules and come with `/give`, the XP with `/xp` if you may run it; otherwise they drop where they stood as if broken by hand. Known gaps: the tool loses no durability, `/loot` gives no XP, and what does not fit in the inventory is lost.
  - **Commands per second:** Never more than this many commands in any one second (10 by default), spread out evenly, so the server does not take them for spam.
  - **Hide success messages:** While a tree or vein is being broken, the server's replies to these commands stay out of your chat. Other operators may still see them.

- **Limits:**
  - **With no area:** What to do when no Build Mode area is in force (Build Mode is off, or no area is marked in this dimension): *Work everywhere* (default) or *Do nothing*. With an area in force only the blocks inside it go; the part of the tree or vein outside it stays as it is.
  - **Blocks per hunger point:** One hunger point (half a drumstick) for every this many blocks broken (10 by default); blocks left over count towards the next job, 0 is free. Paid with a short Hunger effect through the server's `/effect`, saturation first; without `/effect` Vein Breaker does nothing. A job that would take your hunger bar below 7 (where you can no longer sprint) does not start and says so in chat. On Peaceful the hunger bar does not drop, so the cost only takes saturation.
  - **Tool wears** (on by default): The tool in your main hand wears as if you had broken every block with it by hand — its damage per block for each one (1 for a pickaxe or axe, 2 for a sword), with Unbreaking sparing some of it as in the game. Set with the server's `/item`; without `/item`, Vein Breaker does nothing while you hold a tool that wears. The tool never breaks: a job that would use it up does not start and says so in chat.

---

### Build Mode (`buildmode`)
*Marks an area with a wand and keeps the block-breaking modules (Vein Breaker, for now) inside it. The area is drawn in the world and kept per server and dimension; it is still there after a restart, and does not apply in another dimension.*

- **On:** On, the wand works, the area is drawn and the area rule applies. Off, the wand is an ordinary item, the frame is hidden and the area holds nothing back (it is kept, though).
- **Wand:** The items that mark the corners (a wooden sword by default). In hand, a left click on a block marks the first corner and a right click the second; neither breaks nor places anything. Chat shows the corner's coordinates and, once both are marked, the area's size and volume. While the module is on, Auto Weapon and Auto Tool leave the wand alone: they neither take it out of your hand nor put it in the ready slot.
- **Command:** `/bolge` or `/bolge goster` shows the area in chat; `/bolge temizle` clears the area in this dimension; `/bolge kose1` and `/bolge kose2` put a corner at the block you stand in.

---

### Scaffold (`scaffold`)
*While you walk, puts a block under your next step when there is nothing there, so you walk out over a gap. Your view is never turned: the block goes down through the game's own placement, with the same right click you would make on the block beside it. Speed, jumping and the movement sent to the server are left alone.*

- **Placement:**
  - **Delay:** Ticks to wait after putting blocks down (0–20, default 0).
  - **Blocks per tick:** The most blocks in one tick (1–4, default 1).
  - **Fill air / water / lava:** Which gaps get a block (default air and lava on, water off). Grass, snow and the like count as air.
  - **Build against:** *Any block* or *Only listed blocks* (+ the list). The list decides where a bridge may start: the blocks Scaffold put down itself on this bridge always count, so the bridge goes on along itself. An empty list is no limit, any block will do (the list says "List empty: builds against any block" under itself). With something to build against there but the list refusing all of it, the HUD says in red "Scaffold: no listed block to build against". Blocks that do something when used (chests, doors, levers, beds…) are never built against.
- **Ahead and up:**
  - **Reach ahead:** While walking, this many more blocks straight on past the next step (0–5).
  - **Keep Y level:** A bridge stays on the level it started on until it is finished, even over a stair on the way. Jumping never lifts the bridge either way.
  - **Y lock:** *Off* / *Where I started* (the block under your feet when you switched Scaffold on) / *Set Y* (the number below). While on, every block goes in that Y: jumping, or jumping while running, the bridge stays flat. Placed as long as you can reach it. No tower while the lock is on. The locked Y is shown next to the count on the action bar.
  - **Set Y:** The bridge's height while *Set Y* is chosen (−64…319).
  - **Y lock key:** Press it and the lock moves to the block under your feet; on *Set Y* the number changes too. Unbound by default.
  - **Descending bridge:** With the Y lock on, every press of sneak takes the locked Y a block down; the bridge carries on a step lower and **Stop at edges** lets you walk down onto it. Only your own sneak key counts. Off by default.
  - **Ramp:** *Off* / *Up* / *Down*. For every block you go on, the bridge is a block higher or lower, along the way you first walk; with the Y lock on, the lock slides along. Going up, jump at every step (holding jump while walking does it). Every step takes two blocks: the step and the one under it. No tower while on.
  - **Tower:** Jump straight up and a block goes under you once you are a block higher; you land on it (the game's own jump). **Only with jump held** (default on).
- **Bridge shape:**
  - **Width:** 1–3 (default 1). 3 reaches a block out on both sides of you, 2 on the side you are nearer.
  - **Diagonal walking:** Walking slantwise, the corner between two blocks that would meet only at an edge is filled too; without it the bridge stops at the first corner. On by default.
- **Material:**
  - **Blocks to use:** In this order (default cobblestone, cobbled deepslate, dirt, netherrack).
  - **Never use:** Falling blocks (sand, gravel, concrete powder…), TNT, containers like chests and shulker boxes. Only whole blocks you can stand on are ever used.
  - **Prefer full stacks**, **When none of them** (*Largest stack* / *Nearest* / *Nothing*).
  - **Same block:** If you carry the block you are standing on, it is used first; otherwise the list decides. Off by default.
- **Lighting:**
  - **Place torches:** Every **Every N blocks** (1–64, default 12) blocks of bridge, a torch from your inventory goes on the bridge's side face — below the walkway, never in your way. With no room there it goes beside the next block instead. Out of torches, you are told once. Off by default.
- **Safety:**
  - **Stop at edges (SafeWalk):** Holds the game's own sneak. *At an edge*: only when there is no block under the step you are heading for (out of blocks, outside the area…), until you step back or stop sliding. *Always*: the whole time the module is on. Off by default.
  - **Pause with a screen open / while falling / in lava** (all on by default). A fall is one longer than a jump.
  - With Build Mode on and an area marked, blocks go down only inside it.
- **Info:**
  - **Blocks left:** Shows in Scaffold's HUD box how many blocks are left; a red warning below **Warn below** (default 16) and when they run out.
  - **Outline the next block:** A green box where the next block will go. Only a block that can really go down is boxed.
  - **Show on the action bar too:** the HUD box's line is said above the hotbar as well (default off).
- **Hands and inventory:** **Where to look** (hotbar / whole inventory), **Switch back**. In the hand lock it outranks Auto Eat and Auto Tool and gives way to Auto Totem.

### HUD (`hud`)
*Boxes of information on screen: which modules are on and what they have to say. On by default; hidden with F1 and while the debug screen (F3) is open.*

- Under each box's own heading: **Show**, **Corner** (top left, top middle, top right, bottom left, bottom middle, bottom right), **Sideways** / **Up and down** (distance in from the corner), **Size (%)** (50–300).
- **Active modules:** the list of modules that are on (top right by default); **Order** by name / longest first / as in the menu.
- **Scaffold:** blocks left and the locked Y (while Scaffold is on); the lock key, the descending bridge, "out of torches" and "no listed block to build against" lines on top for 3 s.
- **Totems:** totems carried (inventory and both hands); red at 0.
- **Auto Weapon:** seconds left on the fight lock, or "not locked".
- **Vein Breaker:** blocks and percentage while a job runs; no box between jobs.
- **World Downloader:** downloading / not downloading + chunks saved; a red warning if the server refuses.
- In these modules' own settings, **Show on the action bar too**: the line is said above the hotbar as well.
- **HUD editor:** **Edit HUD** on the menu bar. Hold a box with the left button and drag it; dropped, it is kept from the nearest corner, and sticks to an edge closer than 6 units. A right click puts the box back where it started; **Reset all** puts them all back; **Done** returns to the menu.

---

## 4. Profiles, Profile by Server and Presets

### Profiles
- On the left of the menu bar, **Profile: <name> ▾**: a click lists the profiles, a click on one switches to it. **New** (every setting at its default), **Copy** (a copy of the one in use), **Rename**, **Delete** (asks first). The name is typed into the bar; Enter or **OK** takes it, Escape or **Cancel** gives up.
- On a switch the modules that are on are switched off through their own switch-off, then the next profile is read and what is on in it comes on.
- Each profile is `config/survivalkit/profiles/<name>.json`. **default** and **Safe** are built in: they cannot be renamed or deleted.
- **Safe:** every module is off each time it is switched to and when the game starts in it (HUD included).

### Profile by server
- The last line of the profile list, **By server…**, opens the rules screen.
- **Singleplayer:** the profile for a singleplayer world. **When no rule matches:** for a server no rule matches. Either can be **Keep the one in use** (the default).
- **Address rules:** address → profile, top to bottom; the first that matches wins. **+ Add rule**, ✕ deletes. `*` matches anything: `*.example.net` matches `example.net` and every address under it. The port counts only if the rule has one. A profile button: left click for the next profile, right click for the one before.
- On joining a world, if the profile chosen is not the one in use, it is switched to and a short notice shows in the top right: which profile, and which rule (or singleplayer / no rule matched).
- No server has a rule of its own out of the box; the rules you write are in `config/survivalkit/profiles.json`.

### Presets
- At the top of every module's settings, **Presets**: the built-in ones, your own and **+ Save**.
- Built in — Scaffold: **Bridge** (width 1, Y lock where you started, stop at edges), **Ramp** (ramp up, width 1), **Wide road** (width 3, Y lock where you started, torches on); Bridge and Wide road also turn the ramp off. Auto Weapon: **Aim only**, **Crowd**. Auto Eat: **Frugal** (automatic threshold, cheapest first when none of the list is there), **Always fed** (fixed threshold 19, eat early to heal).
- A click changes only that module's settings, only the ones the preset names; it never switches the module on or off and leaves its keys alone. How many settings changed shows beside the title. Hovering a preset lists what it would change.
- **+ Save:** keeps the module's settings as they are (but for on/off and keys) under a name; your own preset's ✕ deletes it. Your own presets are in `config/survivalkit/presets.json` and the same in every profile.
- Each setting that changed has a ↺ that puts it alone back to what it was before the preset.
