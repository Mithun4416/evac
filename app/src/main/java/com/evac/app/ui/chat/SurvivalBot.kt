package com.evac.app.ui.chat

object SurvivalBot {

    data class BotResponse(val title: String, val body: String)

    // ===============================================
    // SURVIVAL KNOWLEDGE BASE
    // ===============================================

    private val survivalTips = listOf(
        Pair(listOf("water", "drink", "thirst", "dehydrate"), BotResponse(
            "Water & Hydration",
            "- Collect rainwater using any container or cloth.\n- If you have a plastic bottle, fill with murky water, let sediment settle, strain through cloth.\n- NEVER drink seawater or urine.\n- Ration water: small sips every 15-20 minutes.\n- Signs of dehydration: dark urine, dizziness, dry mouth - drink immediately."
        )),
        Pair(listOf("first aid", "bleeding", "wound", "injury", "cut", "bandage"), BotResponse(
            "First Aid - Bleeding & Wounds",
            "- Apply firm pressure with clean cloth for 10+ minutes.\n- Elevate the wound above heart level.\n- Do NOT remove embedded objects - stabilize them.\n- If no bandage, use torn clothing strips.\n- Watch for signs of infection: redness, swelling, warmth, pus."
        )),
        Pair(listOf("earthquake", "quake", "tremor", "shaking"), BotResponse(
            "Earthquake Survival",
            "- DROP, COVER, HOLD ON under sturdy furniture.\n- Stay away from windows and heavy objects.\n- If trapped: tap on pipes/walls (3 taps = SOS).\n- Do NOT use elevators after a quake.\n- Expect aftershocks - stay in safe position.\n- Use your phone flashlight to signal rescuers."
        )),
        Pair(listOf("flood", "rain", "drown", "water level", "rising"), BotResponse(
            "Flood Survival",
            "- Move to the highest floor or rooftop IMMEDIATELY.\n- Never walk through flowing water (6 inches can knock you down).\n- Never drive through flooded roads.\n- Signal rescuers with bright clothing or flashlight.\n- If swept away, float on your back, feet downstream.\n- Avoid downed power lines in water."
        )),
        Pair(listOf("fire", "burn", "smoke", "flame"), BotResponse(
            "Fire & Burns",
            "- Stay low - smoke rises. Crawl to safety.\n- Feel doors before opening - hot door = fire on other side.\n- Stop, Drop, Roll if clothing catches fire.\n- Cool burns with clean water for 10+ minutes.\n- Do NOT apply butter/oil to burns.\n- Cover mouth with wet cloth to filter smoke."
        )),
        Pair(listOf("cpr", "heart", "breathing", "unconscious", "choking"), BotResponse(
            "CPR & Choking",
            "- CPR: 30 chest compressions (2 inches deep, fast) then 2 rescue breaths, repeat.\n- Push hard and fast in center of chest.\n- Choking: 5 back blows between shoulder blades then 5 abdominal thrusts (Heimlich).\n- If person is unconscious and breathing, place in recovery position (on side)."
        )),
        Pair(listOf("shelter", "cold", "warm", "hypothermia", "exposure"), BotResponse(
            "Shelter & Warmth",
            "- Use debris, cardboard, or branches for insulation from ground.\n- Body heat: huddle together with others.\n- Stuff clothing with crumpled paper/leaves for insulation.\n- Signs of hypothermia: shivering, confusion, slurred speech.\n- Warm the core first - armpits, neck, groin."
        )),
        Pair(listOf("signal", "rescue", "help", "found", "locate"), BotResponse(
            "Signaling for Rescue",
            "- Use the SOUND BEACON feature on the SOS tab.\n- Flash your phone light in patterns (3 flashes = SOS).\n- Make noise by tapping on pipes, walls, or metal objects.\n- Use bright/reflective materials visible from above.\n- SOS in Morse code: ... --- ... (3 short, 3 long, 3 short)."
        )),
        Pair(listOf("food", "eat", "hungry", "starve", "nutrition"), BotResponse(
            "Finding Food",
            "- You can survive 3 weeks without food but only 3 days without water.\n- Prioritize water over food.\n- Avoid unknown plants/mushrooms - many are poisonous.\n- Canned food is safe even past expiration dates.\n- Ration food in small portions throughout the day."
        )),
        Pair(listOf("panic", "anxiety", "scared", "calm", "stress", "fear"), BotResponse(
            "Managing Panic",
            "- Breathe: Inhale 4 seconds, Hold 4 seconds, Exhale 4 seconds.\n- Focus on what you CAN control, not what you can't.\n- Talk to others - you're not alone.\n- Keep a routine: check supplies, signal, rest.\n- Help is coming. Send an SOS from the SOS tab if you haven't."
        )),
        Pair(listOf("cyclone", "storm", "wind", "hurricane", "tornado"), BotResponse(
            "Cyclone/Storm Survival",
            "- Move to an interior room on the lowest floor.\n- Stay away from windows, doors, and outer walls.\n- Get under sturdy furniture if possible.\n- Do NOT go outside during the eye of the storm.\n- After the storm, watch for downed power lines and structural damage."
        )),
        Pair(listOf("snake", "bite", "insect", "sting", "spider"), BotResponse(
            "Snake & Insect Bites",
            "- Stay calm - elevated heart rate spreads venom faster.\n- Immobilize the bitten limb, keep below heart level.\n- Do NOT suck venom, cut the wound, or apply ice.\n- Remove tight clothing/jewelry near the bite.\n- Note the time of the bite and any symptoms for medics."
        )),
        Pair(listOf("broken", "fracture", "bone", "sprain", "splint"), BotResponse(
            "Fractures & Sprains",
            "- Immobilize the injury - do NOT try to straighten broken bones.\n- Make a splint from stiff material (board, rolled magazine).\n- Pad the splint with cloth for comfort.\n- Apply ice/cold pack if available (20 minutes on, 20 off).\n- Elevate the injured limb to reduce swelling."
        )),
        Pair(listOf("child", "baby", "kid", "infant"), BotResponse(
            "Child Safety",
            "- Keep children calm - reassure them help is coming.\n- Check for injuries they might not verbalize.\n- Keep them warm and hydrated.\n- Distract with simple games or stories.\n- Never leave children unattended near water or debris.\n- Write your name and phone on their arm with marker."
        )),
        Pair(listOf("power", "electricity", "blackout", "generator"), BotResponse(
            "Power Outage",
            "- Turn off appliances to prevent surge damage on restoration.\n- Use phone flashlight sparingly - conserve battery.\n- Enable airplane mode to save battery.\n- Keep fridge/freezer closed (food lasts 4hrs fridge, 48hrs freezer).\n- Never run generators indoors - carbon monoxide kills."
        ))
    )

    // ===============================================
    // GENERAL KNOWLEDGE BASE
    // ===============================================

    private val generalKnowledge = listOf(
        // Greetings
        Pair(listOf("hello", "hi", "hey", "howdy", "sup", "yo"), BotResponse(
            "Hey there!",
            "Hello! I'm Evac Bot - your offline assistant. I can help with:\n\n- Survival & emergency tips\n- General knowledge & trivia\n- Basic math calculations\n- Casual conversation\n\nWhat would you like to know?"
        )),
        Pair(listOf("how are you", "how do you do", "what's up", "whats up"), BotResponse(
            "I'm doing great!",
            "Thanks for asking! I'm running fully offline on your device, ready to help. What can I assist you with today?"
        )),
        Pair(listOf("thank", "thanks", "thx"), BotResponse(
            "You're welcome!",
            "Happy to help! Remember, I'm always here - even without internet. Feel free to ask anything else!"
        )),
        Pair(listOf("bye", "goodbye", "see you", "take care"), BotResponse(
            "Take care!",
            "Stay safe out there! Remember - if you're ever in an emergency, use the SOS tab to signal for help. I'm always here if you need me!"
        )),

        // About the app
        Pair(listOf("evac", "what is this", "about", "app"), BotResponse(
            "About EVAC",
            "EVAC is an offline emergency communication app that works without internet:\n\n- SOS: Send distress signals via mesh network\n- Map: Find safe spots & track responders\n- Help: Get survival tips & general help (that's me!)\n- Bulletin: Receive emergency broadcasts\n- Responder: Dashboard for rescue teams\n\nAll communication uses Bluetooth mesh - no WiFi or cellular needed!"
        )),

        // Science
        Pair(listOf("sun", "solar", "star"), BotResponse(
            "The Sun",
            "- The Sun is a G-type main-sequence star (yellow dwarf)\n- Distance from Earth: ~93 million miles (150 million km)\n- Surface temperature: ~5,500 C (10,000 F)\n- Age: ~4.6 billion years\n- It takes sunlight about 8 minutes and 20 seconds to reach Earth\n- The Sun makes up 99.86% of our solar system's mass"
        )),
        Pair(listOf("moon", "lunar"), BotResponse(
            "The Moon",
            "- The Moon is Earth's only natural satellite\n- Distance from Earth: ~238,855 miles (384,400 km)\n- It takes 27.3 days to orbit Earth\n- The same side always faces Earth (tidal locking)\n- 12 people have walked on the Moon (all Americans, 1969-1972)\n- The Moon's gravity is about 1/6th of Earth's"
        )),
        Pair(listOf("planet", "mars", "jupiter", "venus", "saturn", "mercury", "neptune", "uranus"), BotResponse(
            "Our Solar System",
            "The 8 planets in order from the Sun:\n\n1. Mercury - smallest, closest to Sun\n2. Venus - hottest planet, thick atmosphere\n3. Earth - our home!\n4. Mars - the Red Planet, has the tallest volcano\n5. Jupiter - largest planet, Great Red Spot\n6. Saturn - famous for its rings\n7. Uranus - rotates on its side\n8. Neptune - windiest planet"
        )),
        Pair(listOf("gravity", "newton"), BotResponse(
            "Gravity",
            "- Gravity is the force of attraction between objects with mass\n- Sir Isaac Newton formulated the law of universal gravitation (1687)\n- On Earth, gravitational acceleration = 9.8 m/s squared\n- Einstein's General Relativity describes gravity as spacetime curvature\n- You weigh less on the Moon (1/6th) and more on Jupiter (2.5x)"
        )),

        // Geography
        Pair(listOf("ocean", "sea"), BotResponse(
            "World's Oceans",
            "The 5 oceans by size:\n\n1. Pacific Ocean - largest, covers 30% of Earth\n2. Atlantic Ocean - second largest\n3. Indian Ocean - warmest ocean\n4. Southern (Antarctic) Ocean\n5. Arctic Ocean - smallest and shallowest\n\nOceans cover about 71% of Earth's surface and contain 97% of Earth's water."
        )),
        Pair(listOf("country", "continent", "world", "nation"), BotResponse(
            "World Geography",
            "- 7 continents: Asia, Africa, North America, South America, Antarctica, Europe, Australia/Oceania\n- ~195 countries in the world\n- Largest country: Russia (17.1 million km squared)\n- Smallest country: Vatican City (0.44 km squared)\n- Most populated: India (~1.4 billion)\n- Highest point: Mt. Everest (8,849m)\n- Deepest point: Mariana Trench (10,994m)"
        )),
        Pair(listOf("mountain", "everest", "tall"), BotResponse(
            "Famous Mountains",
            "- Mt. Everest: 8,849m, highest above sea level (Nepal/China)\n- K2: 8,611m, second highest (Pakistan/China)\n- Kangchenjunga: 8,586m, third highest (Nepal/India)\n- Mauna Kea: 10,203m total (tallest from base, Hawaii)\n- Kilimanjaro: 5,895m, highest in Africa\n- Mont Blanc: 4,808m, highest in Western Europe"
        )),

        // History
        Pair(listOf("history", "ancient", "civilization"), BotResponse(
            "Famous Civilizations",
            "- Ancient Egypt: 3100-30 BC, pyramids & pharaohs\n- Mesopotamia: 3500-500 BC, first writing (cuneiform)\n- Ancient Greece: 800-146 BC, democracy & philosophy\n- Roman Empire: 27 BC-476 AD, vast territory & laws\n- Indus Valley: 3300-1300 BC, urban planning\n- Ancient China: 2100 BC+, paper, compass, gunpowder"
        )),
        Pair(listOf("war", "battle", "conflict"), BotResponse(
            "Major World Events",
            "- World War I (1914-1918): 20 million deaths\n- World War II (1939-1945): 70-85 million deaths\n- Cold War (1947-1991): US vs USSR ideological conflict\n- Moon Landing (1969): Neil Armstrong's 'giant leap'\n- Fall of Berlin Wall (1989): end of divided Germany\n- Internet (1990s): transformed global communication"
        )),

        // Animals
        Pair(listOf("animal", "dog", "cat", "pet"), BotResponse(
            "Animal Facts",
            "- Dogs can smell 10,000x better than humans\n- Cats spend 70% of their lives sleeping\n- An octopus has 3 hearts and blue blood\n- Elephants are the only animals that can't jump\n- A group of flamingos is called a 'flamboyance'\n- Dolphins sleep with one eye open"
        )),

        // Human body
        Pair(listOf("body", "human", "organ", "brain", "health"), BotResponse(
            "Human Body Facts",
            "- The human brain has ~86 billion neurons\n- Your heart beats about 100,000 times per day\n- The human body has 206 bones (babies have ~270!)\n- Blood travels about 12,000 miles per day\n- You produce about 1 liter of saliva daily\n- The cornea is the only body part with no blood supply"
        )),

        // Space
        Pair(listOf("space", "universe", "galaxy", "cosmos"), BotResponse(
            "Space Facts",
            "- The observable universe is ~93 billion light-years in diameter\n- There are more stars than grains of sand on Earth\n- A day on Venus is longer than its year\n- Light from the Sun takes 8 min to reach Earth\n- The ISS orbits Earth every 90 minutes\n- Neutron stars are so dense: 1 teaspoon = 6 billion tons"
        )),

        // Technology
        Pair(listOf("computer", "technology", "internet", "phone", "ai", "artificial intelligence"), BotResponse(
            "Technology Facts",
            "- The first computer (ENIAC) weighed 30 tons (1945)\n- The internet was invented in 1969 (ARPANET)\n- The first smartphone was IBM Simon (1994)\n- AI concept was coined at Dartmouth Conference (1956)\n- There are ~5.4 billion internet users worldwide\n- A smartphone today is 100,000x more powerful than the Apollo 11 computer"
        )),

        // Weather
        Pair(listOf("weather", "climate", "temperature", "hot", "cold"), BotResponse(
            "Weather & Climate",
            "- Hottest place: Death Valley, CA (56.7 C / 134 F in 1913)\n- Coldest place: Antarctica (-89.2 C / -128.6 F in 1983)\n- Wettest place: Mawsynram, India (~11,871mm rain/year)\n- Lightning heats air to ~30,000 C (5x hotter than Sun's surface)\n- A hurricane can release energy equal to 10,000 nuclear bombs"
        )),

        // Jokes / Fun
        Pair(listOf("joke", "funny", "laugh", "humor"), BotResponse(
            "Here's a joke!",
            "Why don't scientists trust atoms?\n\n...Because they make up everything!\n\nWant to hear another one? Just ask!"
        )),
        Pair(listOf("another joke", "more jokes", "tell me a joke"), BotResponse(
            "Another one!",
            "What do you call a fake noodle?\n\n...An impasta!\n\nI've got more where that came from!"
        )),

        // Who/What is
        Pair(listOf("who are you", "what are you", "your name"), BotResponse(
            "About Me",
            "I'm Evac Bot - an offline assistant built into the EVAC emergency app.\n\n- I work 100% offline - no internet needed\n- I know survival tips, general knowledge, trivia & more\n- I can do basic math calculations\n- I'm always ready to help, 24/7\n\nEverything runs on your device!"
        )),

        // Time
        Pair(listOf("time", "date", "day", "year"), BotResponse(
            "Time & Date",
            "I don't have access to your device clock, but here's some fun time facts:\n\n- A year is 365.25 days (extra 0.25 = leap year every 4 years)\n- A day is exactly 86,400 seconds\n- The oldest known calendar is ~10,000 years old\n- Time zones were introduced in 1884\n- The longest day of the year is the summer solstice (~June 21 in Northern Hemisphere)"
        )),

        // Language
        Pair(listOf("language", "speak", "word"), BotResponse(
            "Language Facts",
            "- There are approximately 7,000 languages in the world\n- Mandarin Chinese has the most native speakers (~920 million)\n- English is the most widely spoken language overall\n- The oldest written language is Sumerian (~3100 BC)\n- 'Set' has the most definitions of any English word\n- A new word is added to the dictionary every ~2 hours"
        ))
    )

    // ===============================================
    // MATH HANDLER
    // ===============================================

    private fun tryMath(input: String): BotResponse? {
        val cleaned = input.replace(" ", "")
            .replace("\u00d7", "*").replace("x", "*").replace("X", "*")
            .replace("\u00f7", "/")
            .replace("plus", "+").replace("minus", "-")
            .replace("times", "*").replace("divided by", "/")

        // Simple arithmetic: number op number
        val pattern = Regex("^(-?\\d+\\.?\\d*)([+\\-*/^%])(-?\\d+\\.?\\d*)$")
        val match = pattern.find(cleaned) ?: return null

        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else return BotResponse("Math Error", "Can't divide by zero!")
            "^" -> Math.pow(a, b)
            "%" -> a % b
            else -> return null
        }

        val resultStr = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            "%.4f".format(result).trimEnd('0').trimEnd('.')
        }

        return BotResponse("Math Result", "$a $op $b = $resultStr")
    }

    // ===============================================
    // MAIN RESPONSE ENGINE
    // ===============================================

    fun getResponse(userMessage: String): BotResponse {
        val lowerMsg = userMessage.lowercase().trim()

        if (lowerMsg.isEmpty()) return BotResponse(
            "Ask me anything!",
            "Type a question - I can help with survival tips, general knowledge, math, trivia, and more!"
        )

        // 1. Check survival tips first (priority for emergency app)
        for ((keywords, response) in survivalTips) {
            if (keywords.any { lowerMsg.contains(it) }) {
                return response
            }
        }

        // 2. Check general knowledge
        for ((keywords, response) in generalKnowledge) {
            if (keywords.any { lowerMsg.contains(it) }) {
                return response
            }
        }

        // 3. Try math
        val mathResult = tryMath(lowerMsg)
        if (mathResult != null) return mathResult

        // 4. Handle common phrases
        if (lowerMsg.contains("what can you do") || lowerMsg.contains("capabilities")) {
            return BotResponse(
                "What I Can Do",
                "Here's everything I can help with:\n\n" +
                "Survival - water, first aid, fire, flood, earthquake, CPR, shelter\n" +
                "Knowledge - science, geography, history, animals, human body\n" +
                "Space & Tech - planets, stars, computers, AI\n" +
                "Math - basic calculations (try: 42*17 or 365/7)\n" +
                "Fun - jokes, trivia, interesting facts\n" +
                "Chat - say hello, ask about me, or just talk!\n\n" +
                "Just type naturally!"
            )
        }

        if (lowerMsg.contains("yes") || lowerMsg.contains("ok") || lowerMsg.contains("sure") || lowerMsg.contains("yeah")) {
            return BotResponse("Great!", "What else would you like to know? Feel free to ask about anything!")
        }

        if (lowerMsg.contains("no") || lowerMsg.contains("nah") || lowerMsg.contains("nope")) {
            return BotResponse("No problem!", "I'm here whenever you need me. Just type any question!")
        }

        // 5. Fallback - helpful and friendly
        return BotResponse(
            "Let me think...",
            "I don't have a specific answer for \"$userMessage\", but here's what I can help with:\n\n" +
            "Survival - water, fire, earthquake, flood, CPR, first aid\n" +
            "Trivia - science, space, animals, geography, history\n" +
            "Math - try typing a calculation like 25*4\n" +
            "Fun - ask for a joke!\n" +
            "General - say hi, ask about EVAC, or explore topics\n\n" +
            "Try a keyword from the list above!"
        )
    }
}
