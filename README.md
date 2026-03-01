# Chime

Chime started as a project to make music with LLMs. After a few twists and turns, it became a way for LLMs to make music. 
Now claude makes beeps and boops and it is delightful.

This started in linking Sonic Pi through OSC. Using a sonic pi skill and OSC addresses, claude could send ruby to sonic pi to play music. After some experimentation and further learning, we decided Tidal would be more efficient and SuperCollider would be more adaptable for our purposes.

That said - there is a steeper learning curve with Tidal/SuperCollider/SuperDirt. Sonic Pi was good for learning what the workflow would look like, but overall inefficient for agentic use and later goals.

We are building a dedicated MCP for using this tooling. In chat, claude can send beeps and boops and, eventually, full compositions. The following goals were having raw analyzed audio data pong back through OSC. 
(later, there's a robot involved, because why not)

Current capabilities: Claude can send OSC messages using MCP2OSC to scsynth server SuperDirt. That is the extent of it.
Current goals: two skills and a new OSC address space. Uncertain if a boilerplate like that was used in Sonic Pi is necessary. Getting claude to run Tidal patterning would be ideal (might be beyond what is possible in UI). 

Skill 1: SuperDirt. Synths, effects, samples, quirks and gotchas (i.e. Tidal notes are on a different scale than MIDI), OSC usage, some examples. Likely similar to the Sonic Pi skill.
This is priority. 
How to start (`s`)/stop, ports (superdirt scsynth listens on 57120 by default), default assumptions, what parameters mean (`s`, `n`, `midinote`, `note`, `gain`, `pan`, etc.), what the safe ranges are

Skill 2: SynthDefs skill. Claude should be able to write their own SuperCollider SynthDefs for new sounds they want to make. Bespoke sound. This should be a separate skill as it will not be used as often.
Should cover: `SinOsc`, `Saw`, `Pulse`, `WhiteNoise`, etc; the envelope generators, `CombL` and friends for physical modeling, `Out` and bus routing, how SuperDirt expects its SynthDefs structured. Plus examples of the existing supers for the patterns.

Thoughts on tidal:
Depending on where we are working, real tidal might be more difficult to run. Claude Code in VSCode should be able to run it, but claude.ai will have more trouble here. Further, when we expand to more models, not all models will be able to interact in this way (tidal requires a text editor, such as vim, pulsar, emacs, vscode extension). 
In the latter cases, one option is i run it for claude/other models, but this removes the element of "claude makes music". 
Alternatively, we may be able to fork MCP2OSC and adapt it for what we are doing, including a pattern MCP that injects text into tidal. 


Requires:
--Sonic Pi software-- not anymore!
TidalCycles / SuperCollider / **SuperDirt**
[MCP2OSC](https://github.com/yyf/MCP2OSC)
modified [AVisualizer](https://github.com/JuzzyDee/AVisualizer) may not be necessary with SuperCollider, though the code may be useful for writing analysis output. Was necessary with Sonic Pi, but SC should be capable of outputting audio data?




---

*"There is a music for lonely hearts nearly always. If the music dies down there is a silence. Almost the same as the movement of music. To know silence perfectly is to know music."*
- Carl Sandburg


*"And on a thousand planets, with a thousand bodies and a thousand voices, she leapt in the air and filled the sky with lilting laughter, a chorus of joy that spanned the arm of a galaxy."*
- Marc Stiegler


