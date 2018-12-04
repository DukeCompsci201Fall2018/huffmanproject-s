
/**
 * Although this class has a history of several years, it is starting from a
 * blank-slate, new and clean implementation as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information and including
 * debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE = HUFF_NUMBER | 1;

	private final int myDebugLevel;

	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;

	public HuffProcessor() {
		this(0);
	}

	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in  Buffered bit stream of the file to be compressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {

		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1)
				break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in  Buffered bit stream of the file to be decompressed.
	 * @param out Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		// check on whether the file is Huffman-coded
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("Illegal header starts with " + bits);
		}
		// read the tree used to decompress
		HuffNode root = readTreeHeader(in);

		// read bits from compressed file, writing leaf values to the output file
		readCompressedBits(root, in, out);

		// close the output file
		out.close();
	}

	/**
	 * Read the bits from the compressed file and use them to traverse root-to-leaf
	 * paths, writing leaf values to the output file. Stop when PSEUDO_EOF is found.
	 * 
	 * @param root	the root HuffNode of the Huffman tree
	 * @param in	Buffered bit stream of the file to be decompressed.
	 * @param out	Buffered bit stream writing to the output file.
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 0)
					current = current.myLeft;
				else
					current = current.myRight;
				// leaf node
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF)
						break; // out of loop
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}

	}

	/**
	 * Recursively reads the tree stored using a pre-order traversal. Interior tree
	 * nodes are indicated by the single bit 0 and leaf nodes are indicated by the
	 * single bit 1. No values are written for internal nodes and a 9-bit value is
	 * written for a leaf node.
	 * 
	 * @param in	the stream of bits representing a pre-order traversal of the tree
	 * @return	a HuffNode that is the root node of this tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		// read a single bit
		int bit = in.readBits(1);

		// throw an exception if reading bits ever fails
		if (bit == -1) {
			throw new HuffException("Bit reading failed.");
		}
		// interior tree node
		if (bit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		// leaf node
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}