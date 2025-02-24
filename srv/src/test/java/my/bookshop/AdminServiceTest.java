package my.bookshop;

import static cds.gen.adminservice.AdminService_.AUTHORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Collections;

import cds.gen.adminservice.OrderItems_;
import com.sap.cds.CdsList;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.utils.CdsErrorStatuses;

import cds.gen.adminservice.AdminService;
import cds.gen.adminservice.Authors;
import cds.gen.adminservice.OrderItems;
import cds.gen.adminservice.Orders;
import cds.gen.adminservice.Orders_;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AdminServiceTest {

	@Autowired
	private AdminService.Draft adminService;

	@Test
	@WithMockUser(username = "user")
	public void testUnauthorizedAccess() {
		assertThrows(ServiceException.class, () -> {
			adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap()));
		});
	}

	@Test
	@WithMockUser(username = "admin")
	public void testInvalidAuthorName() {
		assertThrows(ServiceException.class, () -> {
			Authors author = Authors.create();
			author.setName("little Joey");
			adminService.run(Insert.into(AUTHORS).entry(author));
		});
	}

	@Test
	@WithMockUser(username = "admin")
	public void testValidAuthorName() {
		Authors author = Authors.create();
		author.setName("Big Joey");
		Result result = adminService.run(Insert.into(AUTHORS).entry(author));
		assertEquals(1, result.rowCount());
	}

	@Test
	@WithMockUser(username = "admin")
	void testCreateOrderWithoutBook() {
		Orders order = Orders.create();
		order.setOrderNo("324");
		order.setShippingAddressId("100");
		OrderItems item = OrderItems.create();
		item.setQuantity(1);
		item.setAmount(BigDecimal.valueOf(12.12));
		order.setItems(Collections.singletonList(item));

		// Runtime ensures that book is present in the order item, when it is created.
		ServiceException exception =
			assertThrows(ServiceException.class, () -> adminService.run(Insert.into(Orders_.class).entry(order)));
		assertEquals(CdsErrorStatuses.VALUE_REQUIRED.getCodeString(), exception.getErrorStatus().getCodeString());
	}

	@Test
	@WithMockUser(username = "admin")
	void testCreateOrderWithNonExistingBook() {
		Orders order = Orders.create();
		order.setOrderNo("324");
		order.setShippingAddressId("100");
		OrderItems item = OrderItems.create();
		item.setQuantity(1);
		item.setAmount(BigDecimal.valueOf(12.12));
		item.setBookId("4a519e61-3c3a-4bd9-ab12-d7e0c5ddaabb");
		order.setItems(Collections.singletonList(item));

		// Runtime ensures that book exists when order item is created.
		ServiceException exception =
			assertThrows(ServiceException.class, () -> adminService.run(Insert.into(Orders_.class).entry(order)));
		assertEquals(CdsErrorStatuses.TARGET_ENTITY_MISSING.getCodeString(), exception.getErrorStatus().getCodeString());
	}

	@Test
	@WithMockUser(username = "admin")
	void testAddItemWithBook() {
		Orders order = Orders.create();
		order.setOrderNo("324");
		order.setShippingAddressId("100");
		OrderItems item = OrderItems.create();
		item.setQuantity(5);
		item.setAmount(BigDecimal.valueOf(12.12));
		item.setBookId("4a519e61-3c3a-4bd9-ab12-d7e0c5329933");
		order.setItems(Collections.singletonList(item));

		Orders createdOrder = adminService.run(Insert.into(Orders_.class).entry(order)).single(Orders.class);

		// Below does not work, ref resolves not to service entity AdminService.Orders, but to database entity my.bookshop.Orders
		// Orders_ ref = createdOrder.ref();
		Orders_ ref = CQL.entity(Orders_.class).filter( o -> o.ID().eq(createdOrder.getId()).and(o.IsActiveEntity().eq(createdOrder.getIsActiveEntity())));

		adminService.addItem(ref, "aebdfc8a-0dfa-4468-bd36-48aabd65e663", 4);

		Orders updatedOrder = adminService.run(Select.from(ref).columns(o -> o.Items().expand())).single(Orders.class);
		assertEquals(2, updatedOrder.getItems().size());
	}

}
