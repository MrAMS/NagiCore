module ram_wrapper(
    inout wire[31:0]    ram_data,       //RAM数据
    output wire[19:0]   ram_addr,       //RAM地址
    output wire[3:0]    ram_be_n,       //RAM字节使能，低有效。如果不使用字节使能，请保持为0
    output wire         ram_ce_n,       //RAM片选，低有效
    output wire         ram_oe_n,       //RAM读使能，低有效
    output wire         ram_we_n,       //RAM写使能，低有效

    output  [31:0]      io_sram_dout,
    input [19:0]        io_sram_addr,
    input [31:0]        io_sram_din,
    input               io_sram_en,
                        io_sram_re,
                        io_sram_we,
    input [3:0]         io_sram_wmask
);

assign ram_addr = io_sram_addr;
wire we = io_sram_en&&io_sram_we;
wire re = !we; // 这样比io_sram_en&&io_sram_re时序要好点
assign ram_be_n = we?~io_sram_wmask:0;
assign ram_ce_n = 0;
assign ram_oe_n = !re;
assign ram_we_n = !we;
assign ram_data = we ? io_sram_din : 32'dz;
assign io_sram_dout = we ? 0 : ram_data;


endmodule