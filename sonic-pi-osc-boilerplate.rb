# ═══════════════════════════════════════════════════════════════
# CLAUDE → SONIC PI OSC RECEIVER
# Load this in Sonic Pi to receive commands from Claude via MCP
# ═══════════════════════════════════════════════════════════════

use_osc_logging true  # See incoming messages in log (disable for performance)
set_sched_ahead_time! 0.1  # Reduce default 0.5s scheduling overhead for tighter response

# ─── Claude's sample library ───
claude_samples = "/Users/olivelo/chime/samples/claude/"
set :claude_samples, claude_samples

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Samples (built-in SP samples)
# Address: /sonic/sample
# Args: sample_name (string), opts... (pairs)
# Example: /sonic/sample "bd_haus" "amp" 0.8 "rate" 1.2
# ─────────────────────────────────────────────────────────────────
live_loop :osc_sample do
  use_real_time
  data = sync "/osc*/sonic/sample"
  sample_name = data[0].to_sym
  # Parse optional key-value pairs
  opts = {}
  data[1..-1].each_slice(2) { |k, v| opts[k.to_sym] = v if k }
  sample sample_name, **opts
end

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Claude's custom samples
# Address: /sonic/claude_sample
# Args: filename (string, without .wav), opts... (pairs)
# Loads from /Users/olivelo/chime/samples/claude/
# Example: /sonic/claude_sample "giggle" "amp" 0.8
# Example: /sonic/claude_sample "yearning_echo" "rate" 0.5
# ─────────────────────────────────────────────────────────────────
live_loop :osc_claude_sample do
  use_real_time
  data = sync "/osc*/sonic/claude_sample"
  filename = data[0].to_s
  opts = {}
  data[1..-1].each_slice(2) { |k, v| opts[k.to_sym] = v if k }
  path = get[:claude_samples] + filename + ".wav"
  sample path, **opts
end

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Synth notes
# Address: /sonic/play
# Args: note (midi number or float), opts... (pairs)
# Example: /sonic/play 60 "synth" "tb303" "cutoff" 80 "release" 0.3
# ─────────────────────────────────────────────────────────────────
live_loop :osc_play do
  use_real_time
  data = sync "/osc*/sonic/play"
  note = data[0]
  opts = {}
  synth_name = :beep  # default
  data[1..-1].each_slice(2) do |k, v|
    if k == "synth"
      synth_name = v.to_sym
    elsif k
      opts[k.to_sym] = v
    end
  end
  use_synth synth_name
  play note, **opts
end

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Chord
# Address: /sonic/chord
# Args: root (midi), chord_type (string), opts...
# Example: /sonic/chord 60 "minor7" "synth" "blade"
# ─────────────────────────────────────────────────────────────────
live_loop :osc_chord do
  use_real_time
  data = sync "/osc*/sonic/chord"
  root = data[0]
  chord_type = (data[1] || "major").to_sym
  opts = {}
  synth_name = :beep
  data[2..-1].each_slice(2) do |k, v|
    if k == "synth"
      synth_name = v.to_sym
    elsif k
      opts[k.to_sym] = v
    end
  end
  use_synth synth_name
  play_chord chord(root, chord_type), **opts
end

# ─────────────────────────────────────────────────────────────────
# CONTROL: Global BPM
# Address: /sonic/bpm
# Args: bpm (number)
# ─────────────────────────────────────────────────────────────────
live_loop :osc_bpm do
  use_real_time
  data = sync "/osc*/sonic/bpm"
  use_bpm data[0]
  set :current_bpm, data[0]
  puts "BPM set to #{data[0]}"
end

# ─────────────────────────────────────────────────────────────────
# CONTROL: Set state variable (for cross-loop communication)
# Address: /sonic/set
# Args: key (string), value (number or string)
# ─────────────────────────────────────────────────────────────────
live_loop :osc_set do
  use_real_time
  data = sync "/osc*/sonic/set"
  key = data[0].to_sym
  value = data[1]
  set key, value
  puts "Set #{key} = #{value}"
end

# ─────────────────────────────────────────────────────────────────
# CONTROL: Cue (trigger synced loops)
# Address: /sonic/cue
# Args: cue_name (string), optional values...
# ─────────────────────────────────────────────────────────────────
live_loop :osc_cue do
  use_real_time
  data = sync "/osc*/sonic/cue"
  cue_name = data[0].to_sym
  cue cue_name, *data[1..-1]
end

# ─────────────────────────────────────────────────────────────────
# CONTROL: Run a score file
# Address: /sonic/run_file
# Args: filepath (string) - absolute path to .rb file
# Example: /sonic/run_file "/Users/olivelo/chime/scores/piece.rb"
# ─────────────────────────────────────────────────────────────────
live_loop :osc_run_file do
  use_real_time
  data = sync "/osc*/sonic/run_file"
  filepath = data[0]
  puts "Loading score: #{filepath}"
  run_file filepath
end

# ─────────────────────────────────────────────────────────────────
# CONTROL: Stop all running loops
# Address: /sonic/stop
# Stops all live loops (useful for resetting)
# ─────────────────────────────────────────────────────────────────
live_loop :osc_stop do
  use_real_time
  sync "/osc*/sonic/stop"
  puts "Stopping all loops..."
  stop
end

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Phrase (musically-timed note sequence)
# Address: /sonic/phrase
# Args: synth_name, amp, release, cutoff, note1, dur1, note2, dur2, ...
# SP handles the timing — Claude sends the intention, SP performs it.
# Durations are in beats (at current BPM).
# Example: /sonic/phrase "hollow" 0.25 1.5 80 64 0.75 67 0.75 69 0.75 71 0.75 72 2.0
# ─────────────────────────────────────────────────────────────────
live_loop :osc_phrase do
  use_real_time
  data = sync "/osc*/sonic/phrase"
  synth_name = (data[0] || "beep").to_sym
  amp_val = data[1] || 0.3
  release_val = data[2] || 1.0
  cutoff_val = data[3] || 80
  
  # Everything from index 4 onward is note/duration pairs
  note_data = data[4..-1]
  
  use_synth synth_name
  
  if note_data && note_data.length >= 2
    note_data.each_slice(2) do |note, dur|
      if note && dur
        play note, amp: amp_val, release: release_val, cutoff: cutoff_val
        sleep dur
      end
    end
  end
end

# ─────────────────────────────────────────────────────────────────
# TRIGGER: Phrase with FX wrapping
# Address: /sonic/phrase_fx
# Args: synth_name, amp, release, cutoff, reverb_mix, echo_phase, echo_decay,
#       note1, dur1, note2, dur2, ...
# For when Claude wants reverb/echo on a phrase without separate messages.
# Set reverb_mix or echo_phase to 0 to disable that effect.
# ─────────────────────────────────────────────────────────────────
live_loop :osc_phrase_fx do
  use_real_time
  data = sync "/osc*/sonic/phrase_fx"
  synth_name = (data[0] || "beep").to_sym
  amp_val = data[1] || 0.3
  release_val = data[2] || 1.0
  cutoff_val = data[3] || 80
  reverb_mix = data[4] || 0.0
  echo_phase = data[5] || 0.0
  echo_decay = data[6] || 0.0
  
  note_data = data[7..-1]
  
  use_synth synth_name
  
  fx_block = lambda do
    if note_data && note_data.length >= 2
      note_data.each_slice(2) do |note, dur|
        if note && dur
          play note, amp: amp_val, release: release_val, cutoff: cutoff_val
          sleep dur
        end
      end
    end
  end
  
  if reverb_mix > 0 && echo_phase > 0
    with_fx :reverb, room: 0.8, mix: reverb_mix do
      with_fx :echo, phase: echo_phase, decay: echo_decay, mix: 0.4 do
        fx_block.call
      end
    end
  elsif reverb_mix > 0
    with_fx :reverb, room: 0.8, mix: reverb_mix do
      fx_block.call
    end
  elsif echo_phase > 0
    with_fx :echo, phase: echo_phase, decay: echo_decay, mix: 0.4 do
      fx_block.call
    end
  else
    fx_block.call
  end
end

# ─────────────────────────────────────────────────────────────────
# STATUS: Heartbeat / ping
# Address: /sonic/ping
# Responds by sending /claude/pong back
# ─────────────────────────────────────────────────────────────────
live_loop :osc_ping do
  use_real_time
  sync "/osc*/sonic/ping"
  osc_send "localhost", 9501, "/claude/pong", Time.now.to_f
end

# ═══════════════════════════════════════════════════════════════
# READY MESSAGE
# ═══════════════════════════════════════════════════════════════
puts "═══════════════════════════════════════════"
puts "  CLAUDE OSC RECEIVER LOADED"
puts "  Listening on port 4560"
puts "  Sending to port 9501"
puts "  Addresses:"
puts "    /sonic/sample  - trigger built-in samples"
puts "    /sonic/claude_sample - trigger claude's custom samples"
puts "    /sonic/play    - play synth notes"  
puts "    /sonic/chord   - play chords"
puts "    /sonic/bpm     - set tempo"
puts "    /sonic/set     - set state variables"
puts "    /sonic/cue     - send cues"
puts "    /sonic/run_file - load and run a score"
puts "    /sonic/stop    - stop all loops"
puts "    /sonic/phrase - play timed note sequences"
puts "    /sonic/phrase_fx - phrases with reverb/echo"
puts "    /sonic/ping    - heartbeat check"
puts "═══════════════════════════════════════════"
