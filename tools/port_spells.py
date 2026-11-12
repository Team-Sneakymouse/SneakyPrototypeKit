"""Turn the MagicSpells prototype-* spells into SneakyPrototypeKit `actions:` lists.

Usage: python tools/port_spells.py <MagicSpells-Data/spells-prototype> <in config.yml> <out config.yml>

Every `command-console: "cast forcecast [playerName] <spell>"` line in the config gets the native
actions for that spell added under it (actions win; the command is the fallback for older jars). Spells with no ported version keep their command and are reported.
Abilities for prototype spells that no config entry used yet are appended (see NEW).
"""
import re
import sys
from pathlib import Path

import yaml

spells = {}
for f in Path(sys.argv[1]).glob("*.yml"):
    spells.update({k: v for k, v in (yaml.safe_load(f.read_text(encoding="utf-8")) or {}).items() if isinstance(v, dict)})


def num(v, d=0.0):
    try:
        return float(v)
    except (TypeError, ValueError):
        return d


def clean(d):
    return {k: (int(v) if isinstance(v, float) and v.is_integer() else v) for k, v in d.items() if v not in (None, "")}


def particle(e, at=None):
    d = {"type": "particles", "particle": str(e.get("particle-name", "crit")).lower(), "count": int(num(e.get("count"), 10)),
         "spread-xz": num(e.get("horiz-spread"), 0.2), "spread-y": num(e.get("vert-spread"), 0.2), "speed": num(e.get("speed"), 0)}
    if e.get("height-offset"):
        d["height"] = num(e["height-offset"])
    if e.get("color"):
        d["color"] = str(e["color"])
    if e.get("material"):
        d["block"] = str(e["material"]).lower()
    if e.get("delay"):
        d["delay"] = int(num(e["delay"]))
    if at:
        d["at"] = at
    return clean(d)


def sound(e, at=None):
    d = {"type": "sound", "sound": str(e.get("sound")), "volume": num(e.get("volume"), 1), "pitch": num(e.get("pitch"), 1)}
    if e.get("effect") == "soundpersonal":
        d["personal"] = True
    if e.get("delay"):
        d["delay"] = int(num(e["delay"]))
    if at:
        d["at"] = at
    return clean(d)


def font(e):
    return clean({"type": "title-animation", "font": e["name"], "start-frame": e.get("start-frame", 0), "interval": e.get("interval", 1),
                  "duration": e.get("duration", 20), "fade-out": e.get("fade-out", 0)})


def effects(spell, positions):
    out = []
    for e in (spell.get("effects") or {}).values():
        if not isinstance(e, dict) or e.get("position") not in positions:
            continue
        at = "caster" if e.get("position") == "caster" and "caster" not in positions[:1] else None
        kind = e.get("effect")
        if kind == "particles":
            out.append(particle(e, at))
        elif kind in ("sound", "soundpersonal"):
            out.append(sound(e, at))
        elif kind == "fontanimation":
            out.append(font(e))
        elif kind == "effectlibentity":
            lib = e.get("effectlib", {})
            if lib.get("class") == "CircleEffect":
                out.append(clean({"type": "ring", "particle": lib.get("particle"), "radius": lib.get("radius", 1), "points": lib.get("particles", 8),
                                  "period": lib.get("period", 1), "iterations": lib.get("iterations", 20), "height": 1.0}))
            elif lib.get("class") == "Vortex":
                out.append({"type": "ring", "particle": lib.get("particle", "witch"), "radius": 1.0, "points": 3, "period": 2,
                            "iterations": 15, "rise": 0.12, "spin": 0.6})
    return out


def ref(name):
    """`spell(mode=full)` -> spell"""
    return re.sub(r"\(.*\)$", "", name.strip())


def target_actions(name):
    """Actions a MagicSpells targeted spell does to its target."""
    s = spells[ref(name)]
    cls = s["spell-class"]
    fx = effects(s, ["target", "caster"])
    if cls.endswith("PotionEffectSpell"):
        return [clean({"type": "potion", "effect": s["type"], "duration": s.get("duration", 100), "amplifier": s.get("strength", 0),
                       "hidden": not bool(s.get("hidden") is False)})] + fx
    if cls.endswith("CombustSpell"):
        return [{"type": "ignite", "ticks": s.get("fire-ticks", 100)}] + fx
    if cls.endswith("HealSpell"):
        return [{"type": "heal", "amount": s.get("heal-amount", 4)}] + fx
    if cls.endswith("ForcetossSpell"):
        return [{"type": "toss", "horizontal": s.get("horizontal-force", 20), "vertical": s.get("vertical-force", 10)}] + fx
    if cls.endswith("LevitateSpell"):
        return [clean({"type": "levitate", "duration": s.get("duration", 10), "distance-change": num(s.get("distance-change"), 0) / 100,
                       "min-distance": s.get("min-distance", 1), "max-distance": min(num(s.get("max-distance"), 50), 60),
                       "cancel-on-damage": s.get("cancel-on-take-damage", True)})] + fx
    if cls.endswith("ExternalCommandSpell"):
        cmd = s["command-to-execute"][0]
        if cmd.startswith("effect %t freeze"):
            return [{"type": "freeze", "ticks": int(cmd.split()[3]) * 20}] + fx
        if cmd.startswith("sudo %t "):
            out = [{"type": "player-command", "command": cmd[len("sudo %t "):]}]
            for later in s.get("command-to-execute-later", []):
                out.append({"type": "player-command", "command": later[len("sudo %t "):], "delay": s.get("command-delay", 20)})
            return out + fx
    if cls.endswith("TargetedMultiSpell"):
        return [a for sub in s["spells"] if not sub.startswith("DELAY") for a in target_actions(sub)] + fx
    if cls.endswith("InvulnerabilitySpell"):
        return [{"type": "invulnerable", "ticks": int(num(s.get("duration"), 3) * 20)}]
    raise KeyError(f"no target port for {name} ({cls})")


def can_target(s, default="players,mobs"):
    raw = str(s.get("can-target", default)).replace(" ", "")
    return [k.replace("nonplayers", "mobs").replace("monsters", "mobs") for k in raw.split(",") if k]


def beam(name):
    s = spells[name]
    b = clean({"type": "beam", "max-distance": s.get("max-distance", 15), "step": s.get("interval", 0.5), "hit-radius": s.get("hit-radius", 0.5),
               "y-offset": s.get("y-offset", 1), "gravity": s.get("gravity"), "stop-on-ground": True,
               "can-target": can_target(s), "trail": effects(s, ["special"]), "on-hit": target_actions(s["spell"])})
    out = effects(s, ["caster"]) + [b]
    for m in s.get("modifiers", []):  # winddance: leaps after casting
        mm = re.match(r"always cast ([\w-]+)\(delay=(\d+)\)", m)
        if mm:
            leap = leap_of(mm.group(1))
            leap["delay"] = int(mm.group(2))
            out.append(leap)
    return out


def leap_of(name):
    s = spells[name]
    return {"type": "leap", "forward": s.get("forward-velocity", 40), "upward": s.get("upward-velocity", 15), "cancel-fall": True}


def burst(name):
    """The 3-ring DummySpell effect spells."""
    s = spells[name]
    fx = list((s.get("effects") or {}).values())
    parts = [e for e in fx if e.get("effect") == "particles"]
    snd = next((e for e in fx if e.get("effect") in ("sound", "soundpersonal")), None)
    b = {"type": "burst", "particle": str(parts[0]["particle-name"]).lower(),
         "counts": [int(num(p.get("count"), 10)) for p in parts], "spreads": [num(p.get("horiz-spread")) for p in parts],
         "spreads-y": [num(p.get("vert-spread")) for p in parts], "heights": [num(p.get("height-offset"), 1) for p in parts],
         "speed": num(parts[0].get("speed"), 0.1)}
    if snd:
        b.update({"sound": snd["sound"], "volume": num(snd.get("volume"), 1), "pitch": num(snd.get("pitch"), 1)})
    out = [clean(b)]
    for m in s.get("modifiers", []):
        if "forceLay" in m:
            out.append({"type": "player-command", "command": "lay", "delay": 5})
        elif "aura-wet" in m:
            out.append({"type": "command", "command": "cast forcecast [playerName] aura-wet"})
    return out


def exp_potion(name):
    s = spells[name]
    buff = re.match(r"always cast ([\w-]+)", s["modifiers"][0]).group(1)
    toast = next(e for e in s["effects"].values() if e.get("effect") == "toast")
    return [{"type": "command", "command": f"cast forcecast [playerName] {buff}"}] + effects(s, ["caster"])[:1] + \
           [{"type": "message", "text": toast["text"]}, {"type": "sound", "sound": "lom:computer.ding", "volume": 0.5, "pitch": 1.2, "personal": True}]


SWEEP = [{"type": "particles", "particle": "sweep_attack", "count": 5, "spread-xz": 0.1, "spread-y": 0.1, "speed": 0.1, "height": 0.5,
          "at": "caster", "repeat": 5, "interval": 3}]

HAND = {
    "prototype-bigleap-leap": lambda: [leap_of("prototype-bigleap-leap")] + SWEEP + [{"type": "sound", "sound": "lom:sfx.evade", "pitch": 1.5}],
    "prototype-jumpback-leap": lambda: [leap_of("prototype-jumpback-leap")] + SWEEP + [{"type": "sound", "sound": "lom:sfx.evade", "pitch": 0.9}],
    "prototype-rocketjump-leap": lambda: [leap_of("prototype-rocketjump-leap"),
        {"type": "particles", "particle": "flame", "count": 50, "spread-xz": 0.1, "spread-y": 0.1, "speed": 0.1, "height": 0.5},
        {"type": "sound", "sound": "entity.firework_rocket.launch", "pitch": 1.5}],
    "prototype-blinkstep-blink": lambda: [
        {"type": "particles", "particle": "witch", "count": 25, "spread": 0.5, "speed": 0.1, "height": 0.5, "at": "caster"},
        {"type": "sound", "sound": "lom:arcane.shock", "volume": 0.1, "pitch": 1.1, "at": "caster"},
        {"type": "blink", "range": 100, "pass-through-ceiling": True},
        {"type": "particles", "particle": "witch", "count": 25, "spread": 0.5, "speed": 0.1, "height": 0.5, "at": "caster", "delay": 2},
        {"type": "potion", "effect": "slowness", "duration": 5, "amplifier": 5},
        {"type": "potion", "effect": "blindness", "duration": 20, "amplifier": 0}],
    "prototype-flightpath-magicCloud": lambda: [{"type": "flight", "x": 5011, "z": 5000, "altitude": 100, "speed": 1.0, "trail-interval": 2,
        "trail": [{"type": "particles", "particle": "cloud", "count": 100, "spread-xz": 1.3, "spread-y": 0, "speed": 0}]}],
    "prototype-flightpath-courtSummons": lambda: [{"type": "flight", "x": 4747, "z": 5454, "altitude": 100, "speed": 1.0, "trail-interval": 2,
        "trail": [{"type": "particles", "particle": "enchant", "count": 100, "spread-xz": 0.5, "spread-y": 0, "speed": 0}]}],
    "prototype-forcepush-solarflare": lambda: [{"type": "forcepush", "radius": 8, "force": 20, "vertical": 5, "max-vertical": 5},
        {"type": "particles", "particle": "flash", "count": 1, "spread": 0, "repeat": 5, "interval": 3, "color": "FFFFFF"}],
    "prototype-magnet-vacuumlootMulti": lambda: [{"type": "magnet", "radius": 20, "velocity": 0.3, "repeat": 19, "interval": 3}],
    "prototype-bagofholding-dummy": lambda: [
        {"type": "title-animation", "font": "lom:voidhole", "start-frame": 0, "interval": 1, "duration": 75, "fade-out": 0},
        {"type": "sound", "sound": "lom:enlarge", "volume": 0.3, "pitch": 0.1, "personal": True},
        {"type": "enderchest", "delay": 65}],
    "prototype-conjurepetals-multi": lambda: [{"type": "conjure", "repeat": 5, "interval": 10, "pickup-delay": 60, "height": 1.5,
        "items": ["item-firePetal 1 20%", "item-icePetal 1 20%", "item-earthPetal 1 20%", "item-arcanePetal 1 20%", "item-lightPetal 1 20%", "item-darkPetal 1 20%"]},
        {"type": "particles", "particle": "totem_of_undying", "count": 25, "spread": 0.5, "speed": 0.1, "height": 1.5, "repeat": 5, "interval": 10},
        {"type": "sound", "sound": "lom:cute.cast", "volume": 0.3, "pitch": 1.1, "personal": True, "repeat": 5, "interval": 10}],
    "prototype-conjurebones-multi": lambda: [{"type": "summon", "entity": "skeleton", "count": 5, "spawn-interval": 20, "spread": 3,
        "lifetime": 140, "helmet": "prototype-invisiblehelmet", "effects": ["fire_resistance 5000 0"], "tick-interval": 7,
        "on-spawn": [{"type": "sound", "sound": "entity.skeleton.ambient", "volume": 0.5, "pitch": 0.8}],
        "on-tick": [{"type": "conjure", "items": ["item-bone 1"], "pickup-delay": 60, "height": 1.5},
                    {"type": "particles", "particle": "smoke", "count": 5, "spread": 0.5, "height": 1.5}],
        "on-end": [{"type": "particles", "particle": "poof", "count": 20, "spread": 0.4, "speed": 0.05, "height": 1},
                   {"type": "conjure", "items": ["item-bone 8"], "pickup-delay": 60, "height": 1}]},
        {"type": "sound", "sound": "lom:cute.cast", "volume": 0.3, "pitch": 1.1, "personal": True}],
    "prototype-devolutionspray-multi": lambda: [{"type": "area", "radius": 10, "can-target": ["mobs"], "repeat": 6, "interval": 17,
        "on-target": [{"type": "age", "chance": 30, "on-success": [{"type": "particles", "particle": "end_rod", "count": 50, "spread": 0.5, "height": 0.5}]}]},
        {"type": "particles", "particle": "warped_spore", "count": 100, "spread-xz": 3, "spread-y": 2, "height": 3, "repeat": 6, "interval": 17},
        {"type": "particles", "particle": "crimson_spore", "count": 100, "spread-xz": 3, "spread-y": 2, "height": 3, "delay": 5, "repeat": 6, "interval": 17},
        {"type": "particles", "particle": "spore_blossom_air", "count": 100, "spread-xz": 3, "spread-y": 2, "height": 3, "delay": 10, "repeat": 6, "interval": 17},
        {"type": "sound", "sound": "lom:poison.cast", "volume": 0.1, "pitch": 1.5, "repeat": 6, "interval": 17}],
    "prototype-soulsiphon-projectile": lambda: [{"type": "sound", "sound": "lom:dark.vortext", "volume": 0.3},
        {"type": "projectile", "velocity": 3, "max-distance": 100, "hit-radius": 1, "controllable": True, "sneak-inverts": True,
         "can-target": ["players", "mobs"],
         "trail": [{"type": "particles", "particle": "soul", "count": 5, "spread": 0.1},
                   {"type": "particles", "particle": "soul_fire_flame", "count": 10, "spread": 0.1, "speed": 0.1}],
         "on-hit": target_actions("prototype-soulsiphon-blind")}],
    "prototype-thunderribbit-chain": lambda: [{"type": "chain", "range": 50, "bounces": 10, "bounce-range": 10, "can-target": ["players", "mobs", "self"],
        "line": [{"type": "particles", "particle": "glow", "count": 3, "spread": 0.1, "speed": 0.2}],
        "on-hit": [{"type": "sound", "sound": "entity.creeper.primed", "volume": 0.5, "pitch": 2.0},
                   {"type": "particles", "particle": "glow", "count": 20, "spread-xz": 0.5, "spread-y": 1, "speed": 0.1},
                   {"type": "sound", "sound": "block.conduit.attack.target", "volume": 0.8, "pitch": 1.5},
                   {"type": "title-animation", "font": "lom:chainlightning", "duration": 11},
                   {"type": "jump-buff", "ticks": 200,
                    "on-start": [{"type": "command", "command": "disguiseplayer [targetName] frog"}],
                    "on-end": [{"type": "command", "command": "undisguiseplayer [targetName]"}],
                    "on-jump": [{"type": "leap", "forward": 3, "upward": 1},
                                {"type": "sound", "sound": "entity.frog.hurt", "volume": 0.8, "pitch": 0.8}]}]}],
    "prototype-flechetterain-offset": lambda: [{"type": "sound", "sound": "entity.arrow.shoot", "pitch": 0.6},
        {"type": "arrow-rain", "range": 40, "radius": 4, "height": 20, "volleys": 10, "interval": 10, "arrows": 15, "damage": 1, "speed": 2.0,
         "on-volley": [{"type": "sound", "sound": "entity.arrow.shoot", "volume": 1.5, "pitch": 1.4}]}],
    "prototype-entomb-webBallThrow": lambda: [{"type": "sound", "sound": "lom:arcane.shock", "volume": 0.1, "pitch": 1.1},
        {"type": "projectile", "velocity": 20, "max-distance": 100, "hit-radius": 1,
         "trail": [{"type": "particles", "particle": "block_marker", "block": "cobweb", "count": 1, "spread": 0}],
         "on-hit": [{"type": "entomb", "material": "cobweb", "duration": 200},
                    {"type": "particles", "particle": "witch", "count": 25, "spread": 0.5, "speed": 0.1, "height": 0.5, "repeat": 2, "interval": 2}]}],
    "prototype-wall-dwarvenwall": lambda: [{"type": "wall", "distance": 3, "width": 7, "height": 3, "material": "stone_bricks", "duration": 140, "rise-interval": 10},
        {"type": "sound", "sound": "block.stone.place", "pitch": 0.6, "repeat": 3, "interval": 10}],
}

# Prototype spells no ability used yet: key -> (spell, name, description, icon model data, cooldown ms, charges)
NEW = {
    "prototype-glowbeam-beam": ("Glow Beam", "Makes whoever you hit glow", 20000),
    "prototype-breathoflife-beam": ("Breath of Life", "Lets whoever you hit breathe underwater", 20000),
    "prototype-psychiccontrol-beam": ("Psychic Control", "Holds whoever you hit in the air in front of you", 30000),
    "prototype-heavywell-beam": ("Heavy Well", "Weighs down whoever you hit", 8000),
    "prototype-soulsiphon-projectile": ("Soul Siphon", "A soul orb you steer; it blinds what it touches", 15000),
    "prototype-thunderribbit-chain": ("Thunder Ribbit", "Lightning jumps between targets and turns them into leaping frogs", 30000),
    "prototype-winddance-beam": ("Wind Dance", "Pulls a target to you while you dash back", 5000),
    "prototype-flechetterain-offset": ("Flechette Rain", "Rains arrows where you look", 30000),
    "prototype-entomb-webBallThrow": ("Web Ball", "Throws a web ball that traps what it hits", 10000),
    "prototype-wall-dwarvenwall": ("Dwarven Wall", "Raises a stone wall in front of you", 15000),
    "prototype-effectSpell-smokebomb": ("Smoke Bomb", "Vanish in a puff of smoke", 30000),
}


def port(name):
    if name in HAND:
        return HAND[name]()
    s = spells[name]
    cls = s["spell-class"]
    if cls.endswith("BeamSpell"):
        return beam(name)
    if name.startswith("prototype-expPotions-"):
        return exp_potion(name)
    if cls.endswith("DummySpell") and name.startswith("prototype-effectSpell-"):
        out = burst(name)
        if name == "prototype-effectSpell-smokebomb":
            out += [{"type": "potion", "effect": "invisibility", "duration": 100, "amplifier": 0, "ambient": True},
                    {"type": "title-animation", "font": "lom:smokeorb", "duration": 51}]
        return out
    raise KeyError(name)


class Dumper(yaml.SafeDumper):
    def increase_indent(self, flow=False, indentless=False):
        return super().increase_indent(flow, False)


def _list(dumper, data):
    flow = all(not isinstance(x, (dict, list)) for x in data)
    return dumper.represent_sequence("tag:yaml.org,2002:seq", data, flow_style=flow)


Dumper.add_representer(list, _list)


def block(actions, indent):
    text = yaml.dump({"actions": actions}, Dumper=Dumper, sort_keys=False, allow_unicode=True, width=200, indent=2)
    return "\n".join(indent + line for line in text.rstrip().splitlines())


def main():
    src = Path(sys.argv[2]).read_text(encoding="utf-8")
    missing = set()

    def repl(m):
        name = m.group(2)
        try:
            # Keep the old command: the running jar uses it until the restart swaps in the native engine.
            return m.group(0) + chr(10) + block(port(name), m.group(1))
        except KeyError:
            missing.add(name)
            return m.group(0)

    out = re.sub(r'^( +)command-console: "cast forcecast \[playerName\] ([\w-]+)"', repl, src, flags=re.M)
    extra = ["", "    ## Native ports of prototype spells that had no ability yet (2026-11-11)"]
    for spell, (title, desc, cooldown) in NEW.items():
        key = spell if spell not in src else spell + "-native"
        extra += [f"    {key}:", f'        name: "{title}"', f'        description: "{desc}"', '        icon-material: "jigsaw"',
                  "        icon-custom-model-data: 1572", block(port(spell), "        "), "        charges: 25", "        stack-size: 1",
                  f"        cooldown: {cooldown}", '        allowed-types: ["ITEM"]', ""]
    out = re.sub(r"\n(## Available icons for items)", "\n".join(extra) + r"\n\n\1", out, count=1)
    Path(sys.argv[3]).write_text(out, encoding="utf-8")
    yaml.safe_load(out)  # must still parse
    print("not ported:", sorted(missing) or "none")


main()
