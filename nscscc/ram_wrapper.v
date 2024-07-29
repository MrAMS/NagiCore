module ram_wrapper(
    input wire          clk,
    input wire          rst,

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
                        io_sram_we,
    input [3:0]         io_sram_wmask
);

reg we;
reg [19:0] addr;
reg [31:0] wdata;
reg [3:0] wmask;

assign ram_addr = addr;
assign ram_be_n = we?~wmask:0;
assign ram_ce_n = 0;
assign ram_oe_n = we;
assign ram_we_n = !we;
assign ram_data = we ? wdata : 32'dz;

//assign io_sram_dout = we ? rdata : ram_data;
assign io_sram_dout = we ? 0 : ram_data;

// reg [19:0] last_raddr;
// reg [31:0] last_wdata;

always @(posedge clk) begin
    if (rst) begin
        we <= 0;
        addr <= 0;
    end else begin
        if(io_sram_en) begin
            addr <= io_sram_addr[19:0];
            we <= io_sram_we;
            wdata <= io_sram_din;
            wmask <= io_sram_wmask;
        end else begin
            we <= 0;
        end
        // else begin    
        // end
    end
end

endmodule