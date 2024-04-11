`include "DPIC_TYPES_DEFINE.sv"
import "DPI-C" function void dpic_bus_read(input `uint32_t addr, input `uint8_t size, output `uint32_t rdata);
import "DPI-C" function void dpic_bus_write(input `uint32_t addr, input `uint8_t wmask, input `uint32_t wdata);

module DPIC_SRAM #(
    parameter ADDR_WIDTH = 32,
    parameter DATA_WIDTH = 32
) (
    input   wire clk,
    input   wire rst,
    input   wire en,
    input   wire [$clog2(DATA_WIDTH)-1:0] wmask,
    input   wire [ADDR_WIDTH-1:0] addr,
    input   wire [DATA_WIDTH-1:0] wdata,
    output  reg [DATA_WIDTH-1:0] rdata
);
wire [DATA_WIDTH-1:0] rdata_wire;

// assign rdata = rdata_reg;


always @(posedge clk) begin
    if (rst) begin
        rdata <= 0;
    end else begin
        if (en) begin
            if(|wmask) begin
                dpic_bus_write(addr, {{8-$clog2(DATA_WIDTH){1'b0}}, wmask}, wdata);
            end else begin
                dpic_bus_read(addr, 4, rdata_wire);
                rdata <= rdata_wire;
            end
        end
    end
end

endmodule