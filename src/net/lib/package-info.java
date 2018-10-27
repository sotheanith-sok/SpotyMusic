/**
 * The net.lib package contains classes that implement the network transport layer.
 *
 * The {@link net.lib.Socket} class implements a TCP-like connection with guaranteed reliable, in-order delivery
 * of data. Socket uses {@link utils.RingBuffer}s to buffer input and output data, allowing the Socket to be used
 * as an input/output stream pair.
 */
package net.lib;