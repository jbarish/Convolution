package project;
public class Main{
  public static void main (String [] args){

	if(args.length != 0 && args.length != 2){
	    throw new IllegalArgumentException("Invalid Arguments! Expected <inputFile.wav> <impulseFile.wav>");
	}
	Audio.init();
	String input;
	String imp;
	if(args.length==0){
	    input = "./PreludeInDm.WAV";
	    imp = "./ab_c.wav";
	}else{
	    input = args[0];
	    imp=args[1];
	}
	
	System.out.println("Reading in input file: " + input);
	double[][] d = Audio.readFileDouble(input);
	
	System.out.println("Reading in impulse file: " + imp);
	double[][] impulse= Audio.readFileDouble(imp);
	

	int sampleConvolveLen= impulse[0].length;
	System.out.println("Using a convolution length of " + sampleConvolveLen);
	
	Convolution cLeft = new Convolution(impulse[0], impulse[0].length + sampleConvolveLen -1);
	Convolution cRight = new Convolution(impulse[1], impulse[0].length + sampleConvolveLen -1);
	
	for(int i = 0; i < d[0].length/sampleConvolveLen; i++){
	    double[] convolvedLeft= cLeft.convolve(d[0], i*sampleConvolveLen, sampleConvolveLen);
	    double[] convolvedRight= cRight.convolve(d[1], i*sampleConvolveLen, sampleConvolveLen);
	    Audio.write(convolvedLeft,convolvedRight, sampleConvolveLen);
	   
	}
	
	if(d.length%sampleConvolveLen!=0){
	    double[] convolvedLeft = cLeft.convolve(d[0], ((int)d.length/sampleConvolveLen)*sampleConvolveLen,d.length%sampleConvolveLen);
	    double[] convolvedRight = cRight.convolve(d[1], ((int)d.length/sampleConvolveLen)*sampleConvolveLen,d.length%sampleConvolveLen);
	    Audio.write(convolvedLeft, convolvedRight,d.length%sampleConvolveLen);
	}

	Audio.write(cLeft.getOverlap(), cRight.getOverlap(), cLeft.getOverlapSize() < cRight.getOverlapSize()?
	      cLeft.getOverlapSize():cRight.getOverlapSize() );
	Audio.flush();
	Audio.disconnect();

    }
}
