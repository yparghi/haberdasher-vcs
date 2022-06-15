## Protobuf vs. Flatbuffers

- Memory: Flatbuffer doesn't parse the bytes into Java fields -- it's all just the encoded 1's and 0's. So flatbuffer would use less memory than protobuf I think.

- Speed: Flat buffer has no parsing step, so it's faster to load into memory.
    - Caveat: I'm not sure how much the verifier step reduces this benefit.

- Disk space: Flatbuffer has the same format in memory (and over the wire?) as on disk. I think that means flatbuffer takes up *more* space because its wire format is more "generous" for easy reading. Protobuf is more compact (I think) because its parsing step unwinds all those tightly packed bytes for usage in Java.

_Overall_: Since my main priority is the size of storage (and assuming reads and writes are relatively infrequent in a VCS), it sounds like protobuf wins due to its optimizing for tightly packed bytes.
