`include "DPIC_TYPES_DEFINE.sv"
// import "DPI-C" function void dpic_bus_read(input `uint32_t addr, input `uint8_t size, output `uint32_t rdata);
// import "DPI-C" function void dpic_bus_write(input `uint32_t addr, input `uint8_t wmask, input `uint32_t wdata);

module DPIC_RAM_1CYC #(
    parameter ADDR_WIDTH = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire en,
    input   wire [ADDR_WIDTH-1:0] addr,
    input   wire re,
    input   wire we,
    input   wire [DATA_WIDTH/8-1:0] wmask,
    input   wire [1:0] size,
    input   wire [DATA_WIDTH-1:0] wdata,
    output  reg [DATA_WIDTH-1:0] rdata
);

always @(*) begin
    rdata = 0;
    if (en&&!rst) begin
        if(we) begin
            dpic_bus_write({{32-ADDR_WIDTH{1'b0}}, addr}, {{8-DATA_WIDTH/8{1'b0}}, wmask}, wdata);
        end else if(re) begin
            dpic_bus_read({{32-ADDR_WIDTH{1'b0}}, addr}, {6'b0, size}, rdata);
        end
    end
end

endmodule