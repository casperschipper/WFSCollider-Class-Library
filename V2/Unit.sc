/*


Udef -> *new { |name, func, args, category|
    name: name of the Udef and corresponding unit
    func: ugen graph function
    args:  array with argName/default pairs
    category: ?

     -> defines a synthdef, and specs for the argumetns of the synthdef
     -> Associates the unitDef with a name in a dictionary.

U -> *new { |defName, args|
	Makes new Unit based on the defName.
	Retrieves the corresponding Udef from a dictionary
	sets the current args


// example

//using builtin Udefs
//looks for the file in the Udefs folder
x  = U(\sine)
x.def.loadSynthDef
x.start
(
x = Udef( \sine, { |freq = 440, amp = 0.1|
	Out.ar( 0, SinOsc.ar( freq, 0, amp ) ) 
} );
)

y = U( \sine, [ \freq, 880 ] );
y.gui;

y.def.loadSynthDef;

y.start;
y.stop;

y.set( \freq, 700 );

(
// a styled gui in user-defined window
w = Window( y.defName, Rect( 300,25,200,200 ) ).front;
w.addFlowLayout;
RoundView.useWithSkin( ( 
	labelWidth: 40, 
	font: Font( Font.defaultSansFace, 10 ), 
	hiliteColor: Color.gray(0.25)
), { 
	SmoothButton( w, 16@16 )
		.label_( ['power', 'power'] )
		.hiliteColor_( Color.green.alpha_(0.5) )
		.action_( [ { y.start }, { y.stop } ] )
		.value_( (y.synths.size > 0).binaryValue );
	y.gui( w );
});
)

*/

Udef : GenericDef {
	
	classvar <>all, <>defsFolder;
	
	var <>func, <>category;
	var <>synthDef;

	*initClass{
		defsFolder = this.filenameSymbol.asString.dirname.dirname +/+ "UnitDefs";
	}

	*new { |name, func, args, category|
		^super.new( name, args ).init( func ).category_( category ? \default );
	}
	
	*prefix { ^"u_" }
		
	init { |inFunc|
		var argNames, values;
		
		func = inFunc;
		
		synthDef = SynthDef( this.class.prefix ++ this.name.asString, func );
		
		argSpecs = ArgSpec.fromSynthDef( synthDef, argSpecs );
		
		argSpecs.do({ |item|
			if( item.name.asString[..4].asSymbol == 'u_' ) {
				item.private = true;
			};
		});
	}
	
	// this may change
	loadSynthDef { |server|
		synthDef.load(server);
	}
	
	sendSynthDef { |server|
		synthDef.send(server);
	}
	
	synthDefName { ^synthDef.name }
	
	load { |server| this.loadSynthDef( server ) }
	send { |server| this.sendSynthDef( server ) }
	
	// these may differ in subclasses of Udef
	createSynth { |unit, server| // create A single synth based on server
		server = server ? Server.default;
		^Synth( this.synthDefName, unit.getArgsFor( server ), server, \addToTail );
	}
	
	setSynth { |unit ...keyValuePairs|
		unit.synths.do(_.set(*keyValuePairs));
	}
	
	printOn { arg stream;
		stream << "a " << this.class.name << "(" <<* [this.name]  <<")"
	}

	storeOn { arg stream;
		stream << this.class.name << "(" <<* [
			this.name.asCompileString, 
			func.asCompileString,
			argSpecs.asCompileString,
			category.asCompileString
		]  <<")"
	}
		
}

U : ObjectWithArgs {
	
	var <def;
	var <>synths;
	var <>disposeOnFree = false;

	
	*new { |defName, args|
		^super.new.init( defName, args ? [] );
	}
	
	*defClass { ^Udef }
	
	init { |inName, inArgs|
		if( inName.isKindOf( this.class.defClass ) ) {
			def = inName;
		} {
			def = this.class.defClass.fromName( inName.asSymbol );
		};
		if( def.notNil ) {	
			args = def.asArgsArray( inArgs );
			this.values.do{ |value|
			    if(value.respondsTo(\unit_)) {
			        value.unit_(this)
			    }
			}
		} { 
			"defName '%' not found".format(inName).warn; 
		};
		synths = [];
	}	
	
	set { |key, value|
		this.setArg( key, value );
		def.setSynth( this, key, value );
	}
	
	get { |key|
		^this.getArg( key );
	}

	mapSet { |key, value|
		var spec = def.getSpec(key);
		if( spec.notNil ) {
		    this.set(key, spec.map(value) )
		} {
		    this.set(key,value)
		}
	}

	mapGet { |key|
		var spec = def.getSpec(key);
		^if( spec.notNil ) {
		    spec.unmap( this.get(key) )
		} {
		    this.get(key)
		}
	}
	
	getArgsFor { |server|
		server = server.asTarget.server;
		^this.args.collect({ |item, i|
			if( i.odd ) {
				item.asControlInputFor( server );
			} {
				item
			}
		});
	}
	
	doesNotUnderstand { |selector ...args| 
		// bypasses errors; warning only if arg not found
		if( selector.isSetter ) { 
			this.set( selector.asGetter, *args ) 
		} {
			^this.get( selector );
		};	
	}
	
	defName_ { |name, keepArgs = true|
	  	this.init( name.asSymbol, if( keepArgs ) { args } { [] }); // keep args
	}

	makeSynth {|target, synthAction|
	    var synth;
	    "makeSynth".postln;
        synth = def.createSynth( this, target );
        synth.postln;
        synth.startAction_({ |synth|
            this.changed( \go, synth );
        });
        synth.freeAction_({ |synth|
            synths.remove( synth );
            this.changed( \end, synth );
            if(disposeOnFree) {
                this.disposeArgsFor(synth.server)
            }
        });
        this.changed( \start, synth );
        synthAction.value( synth );
        synths = synths.add(synth);
	}
	
	makeBundle { |targets, synthAction|
	    ("make B "++targets).postln;
		^targets.asCollection.collect({ |target|
			target.asTarget.server.makeBundle( false, {
			    this.makeSynth(target, synthAction)
			});
		})
	}
	
	start { |target, latency|
		var targets, bundles;
		target = target ? Server.default;
		targets = target.asCollection;
		bundles = this.makeBundle( targets );
		targets.do({ |target, i|
			target.asTarget.server.sendBundle( latency, *bundles[i] );
		});
		if( target.size == 0 ) {
			^synths[0]
		} { 
			^synths;
		};
	}
	
	free { synths.do(_.free) } 
	stop { this.free }
	
	resetSynths { synths = []; } // after unexpected server quit
	resetArgs {
		this.values = this.def.values.deepCopy; 
		def.setSynth( this, *args );
	}

	isPlaying { ^(synths.size != 0) }
	
	defName { ^def !? { def.name } }
	
	printOn { arg stream;
		stream << "a " << this.class.name << "(" <<* [this.defName, args]  <<")"
	}

	storeOn { arg stream;
		stream << this.class.name << "(" <<* [
			( this.defName ? this.def ).asCompileString,
			args.asCompileString
		]  <<")"
	}
	
	asUnit { ^this }

	prepare { |server, loadDef = true|
	    this.def.loadSynthDef;
	    this.values.do{ |val|
	        if( val.respondsTo(\prepare) ) {
                val.prepare(server.asCollection)
            }
        }
    }

	prepareAndStart{ |server|
	    fork{
	        this.prepare(server);
	        server.asCollection.do{ |s|
	            s.sync;
	        };
	        this.start(server);
	    }
	}

	dispose {
	    this.free;
	    this.values.do{ |val|
	        if(val.respondsTo(\dispose)) {
	            val.dispose
	        }
	    }
	}

	disposeArgsFor { |server|
	    this.values.do{ |val|
	        if(val.respondsTo(\disposeFor)) {
	            val.disposeFor(server)
	        }
	    }
	}
}

+ Object {
	asControlInputFor { |server| ^this } // may split between servers
}

+ Symbol { 
	asUnit { |args| ^U( this, args ) }
}
