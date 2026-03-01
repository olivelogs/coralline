/*
Chime project startup file

Load it by calling:

"Users/olivelo/chime/scd/config/superdirt_startup.scd".load

from the supercollider startup file in:

Platform.userAppSupportDir +/+ "startup.scd",

or via the menu File > Open startup file
*/


(
s.reboot { // server options are only updated on reboot
	// configure the sound server: here you could add hardware specific options
	// see http://doc.sccode.org/Classes/ServerOptions.html
	s.options.outDevice = "MacBook Air Speakers"; // or whatever it’s called in your list
	s.options.sampleRate = 44100;
	s.options.numBuffers = 1024 * 256; // increase this if you need to load more samples
	s.options.hardwareBufferSize = 512;
	s.options.blockSize = 64;
	s.options.memSize = 8192 * 8; // increase this if you get "alloc failed" messages
	s.options.numWireBufs = 128; // increase this if you get "exceeded number of interconnect buffers" messages
	s.options.maxNodes = 1024; // increase this if you are getting drop outs and the message "too many nodes"
	s.options.numOutputBusChannels = 2; // set this to your hardware output channel size, if necessary
	s.options.numInputBusChannels = 0; // set this to your hardware output channel size, if necessary
	// boot the server and start SuperDirt
	s.waitForBoot {
		~dirt.stop; // stop any old ones, avoid duplicate dirt (if it is nil, this won't do anything)
		~dirt = SuperDirt(2, s); // two output channels, increase if you want to pan across more channels
		~dirt.loadSoundFiles;
		~dirt.loadSoundFiles("/Users/olivelo/chime/samples/claude/*");   // load samples
		// for example: ~dirt.loadSoundFiles("/Users/myUserName/Dirt/samples/*");
		s.sync; // optionally: wait for samples to be read
		~dirt.start(57120, 0 ! 12);   // start listening on port 57120, create two busses each sending audio to channel 0
		SuperDirt.default = ~dirt; // make this instance available in sclang (optional)
		"SuperDirt running.".postln;
		// optional, needed for convenient access from sclang:
		// (
			//~d1 = ~dirt.orbits[0]; ~d2 = ~dirt.orbits[1]; ~d3 = ~dirt.orbits[2];
			//~d4 = ~dirt.orbits[3]; ~d5 = ~dirt.orbits[4]; ~d6 = ~dirt.orbits[5];
			//~d7 = ~dirt.orbits[6]; ~d8 = ~dirt.orbits[7]; ~d9 = ~dirt.orbits[8];
			//~d10 = ~dirt.orbits[9]; ~d11 = ~dirt.orbits[10]; ~d12 = ~dirt.orbits[11];
		//);
	};

	s.latency = 0.5; // increase this if you get "late" messages


};
);