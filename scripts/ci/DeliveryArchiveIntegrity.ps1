# 标准库解压与最小 ZIP32 结构校验；同一进程只注册一次类型。
if (-not ('DeliveryArchiveIntegrity' -as [type])) {
    $archiveIntegritySource = @'
using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

// 仅验证交付制品使用的单卷 ZIP32；解压完全交给标准库。
public static class DeliveryArchiveIntegrity
{
    public sealed class Entry
    {
        public string Name;
        public long Offset, Data, Compressed, Expanded;
        public uint Crc;
        public ushort Flags, Method;
        public byte[] RawName;
    }
    static void Require(bool ok) { if (!ok) throw new InvalidDataException("MALFORMED_OR_UNVERIFIABLE_ARCHIVE"); }
    static ushort U16(byte[] b, int p) { return BitConverter.ToUInt16(b, p); }
    static uint U32(byte[] b, int p) { return BitConverter.ToUInt32(b, p); }
    static byte[] At(Stream s, long p, int n)
    {
        Require(p >= 0 && n >= 0 && p <= s.Length - n);
        s.Position = p;
        byte[] b = new byte[n]; int used = 0, read;
        while (used < n && (read = s.Read(b, used, n - used)) > 0) used += read;
        Require(used == n); return b;
    }
    static void Extra(byte[] b, Entry local)
    {
        int p = 0;
        while (p < b.Length)
        {
            Require(p + 4 <= b.Length);
            int tag = U16(b, p), size = U16(b, p + 2);
            // 拒绝替代名称和加密扩展；只接受与 ZIP32 字段一致的冗余本地 ZIP64 长度。
            Require(tag != 0x7075 && tag != 0x9901 && tag != 0x0017);
            p += 4; Require(size <= b.Length - p);
            if (tag == 1) {
                Require(local != null && size == 16);
                Require(BitConverter.ToUInt64(b, p) == (ulong)local.Expanded && BitConverter.ToUInt64(b, p + 8) == (ulong)local.Compressed);
            }
            p += size;
        }
    }
    public static Entry[] Validate(Stream s, long maxEntries)
    {
        Require(s.CanSeek && s.Length >= 22);
        int tailSize = (int)Math.Min(s.Length, 65557);
        byte[] tail = At(s, s.Length - tailSize, tailSize);
        int found = -1;
        for (int i = tail.Length - 22; i >= 0; --i)
            if (U32(tail, i) == 0x06054b50 && i + 22 + U16(tail, i + 20) == tail.Length)
            { Require(found == -1); found = i; }
        Require(found >= 0);
        long end = s.Length - tailSize + found;
        Require(U16(tail, found + 4) == 0 && U16(tail, found + 6) == 0);
        int count = U16(tail, found + 10);
        Require(count != 65535 && U16(tail, found + 8) == count);
        if (count > maxEntries) throw new InvalidDataException("Delivery artifact safety limit=archive-entry-count");
        long directory = U32(tail, found + 16), size = U32(tail, found + 12);
        Require(directory + size == end);
        List<Entry> entries = new List<Entry>();
        HashSet<string> names = new HashSet<string>(StringComparer.Ordinal);
        long cursor = directory;
        for (int i = 0; i < count; ++i)
        {
            byte[] h = At(s, cursor, 46);
            Require(U32(h, 0) == 0x02014b50 && U16(h, 34) == 0 && U16(h, 6) <= 20);
            Entry e = new Entry();
            e.Flags = U16(h, 8); e.Method = U16(h, 10);
            Require((e.Flags & ~0x080e) == 0 && (e.Method == 0 || e.Method == 8));
            Require(e.Method == 8 || (e.Flags & 6) == 0);
            e.Crc = U32(h, 16); e.Compressed = U32(h, 20); e.Expanded = U32(h, 24); e.Offset = U32(h, 42);
            Require(e.Compressed != uint.MaxValue && e.Expanded != uint.MaxValue && e.Offset < directory);
            int nameSize = U16(h, 28), extraSize = U16(h, 30), commentSize = U16(h, 32);
            Require(nameSize > 0 && cursor + 46 + nameSize + extraSize + commentSize <= end);
            e.RawName = At(s, cursor + 46, nameSize);
            if ((e.Flags & 0x800) == 0) foreach (byte b in e.RawName) Require(b < 128);
            e.Name = new UTF8Encoding(false, true).GetString(e.RawName);
            Require(e.Name.IndexOf('\0') < 0 && names.Add(e.Name.Replace('\\', '/')));
            Extra(At(s, cursor + 46 + nameSize, extraSize), null);
            entries.Add(e); cursor += 46 + nameSize + extraSize + commentSize;
        }
        Require(cursor == end);
        entries.Sort(delegate(Entry a, Entry b) { return a.Offset.CompareTo(b.Offset); });
        long boundary = 0;
        for (int i = 0; i < entries.Count; ++i)
        {
            Entry e = entries[i]; Require(e.Offset == boundary);
            byte[] h = At(s, e.Offset, 30);
            Require(U32(h, 0) == 0x04034b50 && U16(h, 4) <= 20 && U16(h, 6) == e.Flags && U16(h, 8) == e.Method);
            int nameSize = U16(h, 26), extraSize = U16(h, 28);
            Require(nameSize == e.RawName.Length);
            byte[] name = At(s, e.Offset + 30, nameSize);
            for (int j = 0; j < name.Length; ++j) Require(name[j] == e.RawName[j]);
            Extra(At(s, e.Offset + 30 + nameSize, extraSize), e);
            e.Data = e.Offset + 30 + nameSize + extraSize;
            long next = i + 1 < entries.Count ? entries[i + 1].Offset : directory;
            Require(e.Data <= next && e.Compressed <= next - e.Data);
            boundary = e.Data + e.Compressed;
            bool descriptor = (e.Flags & 8) != 0;
            if (!descriptor) Require(U32(h, 14) == e.Crc && U32(h, 18) == e.Compressed && U32(h, 22) == e.Expanded);
            else
            {
                Require((U32(h, 14) == 0 || U32(h, 14) == e.Crc) &&
                    (U32(h, 18) == 0 || U32(h, 18) == e.Compressed) &&
                    (U32(h, 22) == 0 || U32(h, 22) == e.Expanded));
                long length = next - boundary; Require(length == 12 || length == 16);
                byte[] d = At(s, boundary, (int)length); int p = length == 16 ? 4 : 0;
                if (p == 4) Require(U32(d, 0) == 0x08074b50);
                Require(U32(d, p) == e.Crc && U32(d, p + 4) == e.Compressed && U32(d, p + 8) == e.Expanded);
                boundary += length;
            }
            Require(boundary == next);
            Require(e.Method != 0 || e.Compressed == e.Expanded);
            Require(e.Method != 8 || e.Compressed > 0);
        }
        Require(boundary == directory); return entries.ToArray();
    }
    // 限定物理压缩区间；逐字节供给 Deflate，避免预读吞掉尾随垃圾后伪装成完整消费。
    sealed class Slice : Stream
    {
        readonly Stream source; readonly long length; readonly bool single;
        long used;
        public Slice(Stream s, Entry e) { source = s; s.Position = e.Data; length = e.Compressed; single = e.Method == 8; }
        public override int Read(byte[] b, int o, int n)
        {
            // 标准库只有读到最终块才会自行结束；输入耗尽仍请求数据表示流未完成。
            if (n > 0 && single && used == length) throw new InvalidDataException("MALFORMED_OR_UNVERIFIABLE_ARCHIVE");
            n = (int)Math.Min(n, length - used); if (single) n = Math.Min(n, 1);
            int read = source.Read(b, o, n); used += read; return read;
        }
        public override bool CanRead { get { return true; } }
        public override bool CanSeek { get { return false; } }
        public override bool CanWrite { get { return false; } }
        public override long Length { get { return length; } }
        public override long Position { get { return used; } set { throw new NotSupportedException(); } }
        public override void Flush() { }
        public override long Seek(long o, SeekOrigin w) { throw new NotSupportedException(); }
        public override void SetLength(long n) { throw new NotSupportedException(); }
        public override void Write(byte[] b, int o, int n) { throw new NotSupportedException(); }
    }
    static readonly uint[] CrcTable = MakeTable();
    static uint[] MakeTable()
    {
        uint[] table = new uint[256];
        for (uint i = 0; i < 256; ++i) { uint c = i; for (int j = 0; j < 8; ++j) c = (c & 1) != 0 ? 0xedb88320U ^ (c >> 1) : c >> 1; table[i] = c; }
        return table;
    }
    public static byte[] ReadContent(Stream archive, Entry entry, long limit, string rule)
    {
        using (Slice slice = new Slice(archive, entry))
        using (Stream input = entry.Method == 8 ? (Stream)new DeflateStream(slice, CompressionMode.Decompress, true) : slice)
        using (MemoryStream output = new MemoryStream())
        {
            byte[] buffer = new byte[8192]; uint crc = 0xffffffffU; int n;
            while ((n = input.Read(buffer, 0, buffer.Length)) > 0)
            {
                if (output.Length + n > limit) throw new InvalidDataException("Delivery artifact safety limit=" + rule);
                for (int i = 0; i < n; ++i) crc = CrcTable[(crc ^ buffer[i]) & 255] ^ (crc >> 8);
                output.Write(buffer, 0, n);
            }
            Require(slice.Position == slice.Length && output.Length == entry.Expanded && (crc ^ 0xffffffffU) == entry.Crc);
            return output.ToArray();
        }
    }
}

'@
    if ($PSVersionTable.PSEdition -eq 'Desktop') {
        Add-Type -TypeDefinition $archiveIntegritySource -ReferencedAssemblies System.IO.Compression
    } else { Add-Type -TypeDefinition $archiveIntegritySource }
}
