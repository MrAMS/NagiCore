module DPIC_SRAM #(
    parameter ADDR_WIDTH = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire reset,
    input   wire en,
    input   wire [$clog2(DATA_WIDTH)-1:0] wmask,
    input   wire [ADDR_WIDTH-1:0] addr,
    input   wire [DATA_WIDTH-1:0] wdata,
    output  wire [DATA_WIDTH-1:0] rdata
);
    
endmodule