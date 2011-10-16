// collects and analyzes a UChain's inputs and outputs

UChainAudioAnalyzer {
	
	var <chain;
	var <ins, <outs;
	
	*new { |chain|
		^super.newCopyArgs( chain ).init;
	}
	
	*inGetter { ^\getAudioIn }
	*outGetter { ^\getAudioOut }
	*defInGetter { ^\audioIns }
	*defOutGetter { ^\audioOuts }
	
	*getterFor { |mode = \in|
		^switch( mode, \in, this.inGetter, \out, this.outGetter );
	}
	
	*defGetterFor { |mode = \in|
		^switch( mode, \in, this.defInGetter, \out, this.defOutGetter );
	}
	
	init { 
		
		var units;
		units = chain.units.collect({ |unit|
			if( unit.class == MetaU ) { 
				unit.unit 
			} {
				unit;
			};
		});
		
		// collects ins and outs in format:
		// [  unit, index-of-unit, [indices], [buses] ]
		
		ins = units.collect({ |unit, i| 
			[ unit, i, unit.def.perform( this.class.defInGetter ) ]; 
		})			
			.select({ |item| item[2].size > 0 })
			.collect({ |item|
				item ++ [ 
					item[2].collect({ |index| 
						item[0].perform( this.class.inGetter, index ) 
					}) 
				];
			});
		
		outs = units.collect({ |unit, i| 
			[ unit, i, unit.def.perform( this.class.defOutGetter ) ]; 
		})			
			.select({ |item| item[2].size > 0 })
			.collect({ |item|
				item ++ [ 
					item[2].collect({ |index| 
						item[0].perform( this.class.outGetter, index ) 
					}) 
				];
			});
		
		this.changed( \init );
	}
	
	// analysis
	
	usedBuses { // all used buses
		
		var buses = Set();
		
		ins.do({ |item| item[ 3 ].do({ |bus| buses.add( bus ) }); });
		outs.do({ |item| item[ 3 ].do({ |bus| buses.add( bus ) }); });
		
		^buses.asArray
	}
	
	busSource { |bus = 0, i = 0| // returns index of unit that outputs to bus before i
		var item;
		if( i > 0 ) {
			^outs.reverse.detect({ |item| 
				item[3].includesEqual( bus ) && { item[1] < i } 
			});
		} {
			^nil;
		};
	}
	
	busDest { |bus = 0, i = 0| // returns [unit, index-of-unit] that gets input from bus after i
		^ins.detect({ |item| item[3].includesEqual( bus ) && { item[1] > i } });
	}
	
	busConnection { |mode = \in, bus = 0, i = 0|
		^switch( mode, \in, { this.busSource( bus, i ) }, \out, { this.busDest( bus, i ) } );
	}
	
	insFor { |i = 0|
		^ins.detect({ |item| item[1] == i });
	}
	
	outsFor { |i = 0|
		^outs.detect({ |item| item[1] == i });
	}
	
	ioFor { |mode = \in, i = 0|
		^switch( mode, \in, { this.insFor( i ) }, \out, { this.outsFor( i ) } );
	}
		
	numInputsFor { |i = 0|
		i = this.insFor( i );
		^if( i.notNil ) { i[ 2 ].size } { 0 };
	}
	
	numOutputsFor { |i = 0|
		i = this.outsFor( i );
		^if( i.notNil ) { i[ 2 ].size } { 0 };
	}
	
	numIOFor { |i = 0|
		^this.numInputsFor( i ) + this.numOutputsFor( i );
	}
	
}

UChainControlAnalyzer : UChainAudioAnalyzer {
	
	*inGetter { ^\getControlIn }
	*outGetter { ^\getControlOut }
	*defInGetter { ^\controlIns }
	*defOutGetter { ^\controlOuts }
	
	
	/*
	init { 
		
		var units;
		units = chain.units.collect({ |unit|
			if( unit.class == MetaU ) { 
				unit.unit 
			} {
				unit;
			};
		});
		
		// collects ins and outs in format:
		// [  unit, index-of-unit, [indices], [buses] ]
		
		ins = units.collect({ |unit, i| [ unit, i, unit.def.controlIns ]; })			.select({ |item| item[2].size > 0 })
			.collect({ |item|
				item ++ [ item[2].collect({ |index| item[0].getControlIn( index ) }) ];
			});
		
		outs = units.collect({ |unit, i| [ unit, i, unit.def.controlOuts ]; })
			.select({ |item| item[2].size > 0 })
			.collect({ |item|
				item ++ [ item[2].collect({ |index| item[0].getControlOut( index ) }) ];
			});
		
	}
	*/
	
}