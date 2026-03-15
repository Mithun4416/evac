package com.evac.app.ui.chat

object SurvivalBot {

    data class BotResponse(val title: String, val body: String)

    private val tips = listOf(
        Pair(listOf("water", "drink", "thirst", "dehydrate"), BotResponse(
            "💧 Water & Hydration",
            "• Collect rainwater using any container or cloth.\n• If you have a plastic bottle, fill with murky water → let sediment settle → strain through cloth.\n• NEVER drink seawater or urine.\n• Ration water: small sips every 15-20 minutes.\n• Signs of dehydration: dark urine, dizziness, dry mouth → drink immediately."
        )),
        Pair(listOf("first aid", "bleeding", "wound", "injury", "cut", "bandage"), BotResponse(
            "🩹 First Aid — Bleeding & Wounds",
            "• Apply firm pressure with clean cloth for 10+ minutes.\n• Elevate the wound above heart level.\n• Do NOT remove embedded objects — stabilize them.\n• If no bandage, use torn clothing strips.\n• Watch for signs of infection: redness, swelling, warmth, pus."
        )),
        Pair(listOf("earthquake", "quake", "tremor", "shaking"), BotResponse(
            "🏚️ Earthquake Survival",
            "• DROP, COVER, HOLD ON under sturdy furniture.\n• Stay away from windows and heavy objects.\n• If trapped: tap on pipes/walls (3 taps = SOS).\n• Do NOT use elevators after a quake.\n• Expect aftershocks — stay in safe position.\n• Use your phone flashlight to signal rescuers."
        )),
        Pair(listOf("flood", "rain", "drown", "water level", "rising"), BotResponse(
            "🌊 Flood Survival",
            "• Move to the highest floor or rooftop IMMEDIATELY.\n• Never walk through flowing water (6 inches can knock you down).\n• Never drive through flooded roads.\n• Signal rescuers with bright clothing or flashlight.\n• If swept away, float on your back, feet downstream.\n• Avoid downed power lines in water."
        )),
        Pair(listOf("fire", "burn", "smoke", "flame"), BotResponse(
            "🔥 Fire & Burns",
            "• Stay low — smoke rises. Crawl to safety.\n• Feel doors before opening — hot door = fire on other side.\n• Stop, Drop, Roll if clothing catches fire.\n• Cool burns with clean water for 10+ minutes.\n• Do NOT apply butter/oil to burns.\n• Cover mouth with wet cloth to filter smoke."
        )),
        Pair(listOf("cpr", "heart", "breathing", "unconscious", "choking"), BotResponse(
            "❤️ CPR & Choking",
            "• CPR: 30 chest compressions (2 inches deep, fast) → 2 rescue breaths → repeat.\n• Push hard and fast in center of chest.\n• Choking: 5 back blows between shoulder blades → 5 abdominal thrusts (Heimlich).\n• If person is unconscious and breathing, place in recovery position (on side)."
        )),
        Pair(listOf("shelter", "cold", "warm", "hypothermia", "exposure"), BotResponse(
            "🏕️ Shelter & Warmth",
            "• Use debris, cardboard, or branches for insulation from ground.\n• Body heat: huddle together with others.\n• Stuff clothing with crumpled paper/leaves for insulation.\n• Signs of hypothermia: shivering, confusion, slurred speech.\n• Warm the core first — armpits, neck, groin."
        )),
        Pair(listOf("signal", "rescue", "help", "found", "locate"), BotResponse(
            "📡 Signaling for Rescue",
            "• Use the SOUND BEACON feature (📢) on the SOS tab.\n• Flash your phone light in patterns (3 flashes = SOS).\n• Make noise by tapping on pipes, walls, or metal objects.\n• Use bright/reflective materials visible from above.\n• SOS in Morse code: ••• --- ••• (3 short, 3 long, 3 short)."
        )),
        Pair(listOf("food", "eat", "hungry", "starve", "nutrition"), BotResponse(
            "🍞 Finding Food",
            "• You can survive 3 weeks without food but only 3 days without water.\n• Prioritize water over food.\n• Avoid unknown plants/mushrooms — many are poisonous.\n• Canned food is safe even past expiration dates.\n• Ration food in small portions throughout the day."
        )),
        Pair(listOf("panic", "anxiety", "scared", "calm", "stress", "fear"), BotResponse(
            "🧘 Managing Panic",
            "• Breathe: Inhale 4 seconds → Hold 4 seconds → Exhale 4 seconds.\n• Focus on what you CAN control, not what you can't.\n• Talk to others — you're not alone.\n• Keep a routine: check supplies, signal, rest.\n• Help is coming. Send an SOS from the SOS tab if you haven't."
        )),
        Pair(listOf("cyclone", "storm", "wind", "hurricane", "tornado"), BotResponse(
            "🌪️ Cyclone/Storm Survival",
            "• Move to an interior room on the lowest floor.\n• Stay away from windows, doors, and outer walls.\n• Get under sturdy furniture if possible.\n• Do NOT go outside during the eye of the storm.\n• After the storm, watch for downed power lines and structural damage."
        )),
        Pair(listOf("snake", "bite", "insect", "sting", "spider"), BotResponse(
            "🐍 Snake & Insect Bites",
            "• Stay calm — elevated heart rate spreads venom faster.\n• Immobilize the bitten limb, keep below heart level.\n• Do NOT suck venom, cut the wound, or apply ice.\n• Remove tight clothing/jewelry near the bite.\n• Note the time of the bite and any symptoms for medics."
        )),
        Pair(listOf("broken", "fracture", "bone", "sprain", "splint"), BotResponse(
            "🦴 Fractures & Sprains",
            "• Immobilize the injury — do NOT try to straighten broken bones.\n• Make a splint from stiff material (board, rolled magazine).\n• Pad the splint with cloth for comfort.\n• Apply ice/cold pack if available (20 minutes on, 20 off).\n• Elevate the injured limb to reduce swelling."
        )),
        Pair(listOf("child", "baby", "kid", "infant"), BotResponse(
            "👶 Child Safety",
            "• Keep children calm — reassure them help is coming.\n• Check for injuries they might not verbalize.\n• Keep them warm and hydrated.\n• Distract with simple games or stories.\n• Never leave children unattended near water or debris.\n• Write your name and phone on their arm with marker."
        )),
        Pair(listOf("power", "electricity", "blackout", "generator"), BotResponse(
            "⚡ Power Outage",
            "• Turn off appliances to prevent surge damage on restoration.\n• Use phone flashlight sparingly — conserve battery.\n• Enable airplane mode to save battery.\n• Keep fridge/freezer closed (food lasts 4hrs fridge, 48hrs freezer).\n• Never run generators indoors — carbon monoxide kills."
        ))
    )

    fun getResponse(userMessage: String): BotResponse {
        val lowerMsg = userMessage.lowercase().trim()

        if (lowerMsg.isEmpty()) return BotResponse("👋 Ask me anything!", "Type a question about survival — water, first aid, earthquakes, fires, signaling for help, and more.")

        for ((keywords, response) in tips) {
            if (keywords.any { lowerMsg.contains(it) }) {
                return response
            }
        }

        return BotResponse(
            "🤖 Survival Tips",
            "I'm not sure about that specific topic. Here's what I can help with:\n\n" +
            "• 💧 Water & hydration\n• 🩹 First aid & bleeding\n• 🏚️ Earthquake safety\n• 🌊 Flood survival\n• 🔥 Fire & burns\n• ❤️ CPR & choking\n• 🏕️ Shelter & warmth\n• 📡 Signaling rescue\n• 🧘 Managing panic\n• 🌪️ Storm/cyclone\n• 🐍 Snake/insect bites\n• 🦴 Fractures\n• 👶 Child safety\n• ⚡ Power outage\n\nTry typing one of these topics!"
        )
    }
}
